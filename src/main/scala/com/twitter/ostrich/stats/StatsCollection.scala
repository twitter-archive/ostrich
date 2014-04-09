/*
 * Copyright 2009 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.ostrich.stats

import com.twitter.conversions.string._
import com.twitter.json.{Json, JsonSerializable}
import com.twitter.util.{Local, Try}
import java.lang.management._
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable

/**
 * Concrete StatsProvider that tracks counters and timings.
 */
class StatsCollection extends StatsProvider with JsonSerializable {
  import scala.collection.JavaConverters._

  protected val counterMap = new ConcurrentHashMap[String, Counter]()
  protected val fastCounterMap = new ConcurrentHashMap[String, Incrementable]()
  protected val metricMap = new ConcurrentHashMap[String, Metric]()
  protected val gaugeMap = new ConcurrentHashMap[String, () => Double]()
  protected val labelMap = new ConcurrentHashMap[String, String]()

  private val listeners = new mutable.ListBuffer[StatsListener]

  /** Set this to true to have the collection fill in a set of automatic gauges from the JVM. */
  var includeJvmStats = false

  /**
   * Use JMX (shudder) to fill in stats about the JVM into a mutable map.
   */
  def fillInJvmGauges(out: mutable.Map[String, Double]) {
    val mem = ManagementFactory.getMemoryMXBean()

    val heap = mem.getHeapMemoryUsage()
    out += ("jvm_heap_committed" -> heap.getCommitted())
    out += ("jvm_heap_max" -> heap.getMax())
    out += ("jvm_heap_used" -> heap.getUsed())

    val nonheap = mem.getNonHeapMemoryUsage()
    out += ("jvm_nonheap_committed" -> nonheap.getCommitted())
    out += ("jvm_nonheap_max" -> nonheap.getMax())
    out += ("jvm_nonheap_used" -> nonheap.getUsed())

    val threads = ManagementFactory.getThreadMXBean()
    out += ("jvm_thread_daemon_count" -> threads.getDaemonThreadCount().toLong)
    out += ("jvm_thread_count" -> threads.getThreadCount().toLong)
    out += ("jvm_thread_peak_count" -> threads.getPeakThreadCount().toLong)

    val runtime = ManagementFactory.getRuntimeMXBean()
    out += ("jvm_start_time" -> runtime.getStartTime())
    out += ("jvm_uptime" -> runtime.getUptime())

    val os = ManagementFactory.getOperatingSystemMXBean()
    out += ("jvm_num_cpus" -> os.getAvailableProcessors().toLong)
    os match {
      case unix: com.sun.management.UnixOperatingSystemMXBean =>
        out += ("jvm_fd_count" -> unix.getOpenFileDescriptorCount)
        out += ("jvm_fd_limit" -> unix.getMaxFileDescriptorCount)
      case _ =>   // ew, Windows... or something
    }

    val compilation = ManagementFactory.getCompilationMXBean()
    out += ("jvm_compilation_time_msec" -> compilation.getTotalCompilationTime())

    val classes = ManagementFactory.getClassLoadingMXBean()
    out += ("jvm_classes_total_loaded" -> classes.getTotalLoadedClassCount())
    out += ("jvm_classes_total_unloaded" -> classes.getUnloadedClassCount())
    out += ("jvm_classes_current_loaded" -> classes.getLoadedClassCount().toLong)

    var postGCTotalUsage = 0L
    var currentTotalUsage = 0L
    ManagementFactory.getMemoryPoolMXBeans().asScala.foreach { pool =>
      val name = pool.getName.regexSub("""[^\w]""".r) { m => "_" }
      Option(pool.getCollectionUsage).foreach { usage =>
        out += ("jvm_post_gc_" + name + "_used" -> usage.getUsed)
        postGCTotalUsage += usage.getUsed
        out += ("jvm_post_gc_" + name + "_max" -> usage.getMax)
      }
      Option(pool.getUsage) foreach { usage =>
        out += ("jvm_current_mem_" + name + "_used" -> usage.getUsed)
        currentTotalUsage += usage.getUsed
        out += ("jvm_current_mem_" + name + "_max" -> usage.getMax)
      }
    }
    out += ("jvm_post_gc_used" -> postGCTotalUsage)
    out += ("jvm_current_mem_used" -> currentTotalUsage)

    // `BufferPoolMXBean` and `ManagementFactory.getPlatfromMXBeans` are introduced in Java 1.7.
    // Use reflection to add these gauges so we can still compile with 1.6
    for {
      bufferPoolMXBean <- Try[Class[_]] {
        ClassLoader.getSystemClassLoader.loadClass("java.lang.management.BufferPoolMXBean")
      }
      getPlatformMXBeans <- classOf[ManagementFactory].getMethods.find { m =>
        m.getName == "getPlatformMXBeans" && m.getParameterTypes.length == 1
      }
      pool <- getPlatformMXBeans.invoke(null /* static method */, bufferPoolMXBean)
        .asInstanceOf[java.util.List[_]].asScala
    } {
      val name = bufferPoolMXBean.getMethod("getName").invoke(pool).asInstanceOf[String]

      val getCount: Method = bufferPoolMXBean.getMethod("getCount")
      out += ("jvm_buffer_" + name + "_count" -> getCount.invoke(pool).asInstanceOf[Long])

      val getMemoryUsed: Method = bufferPoolMXBean.getMethod("getMemoryUsed")
      out += ("jvm_buffer_" + name + "_used" -> getMemoryUsed.invoke(pool).asInstanceOf[Long])

      val getTotalCapacity: Method = bufferPoolMXBean.getMethod("getTotalCapacity")
      out += ("jvm_buffer_" + name + "_max" -> getTotalCapacity.invoke(pool).asInstanceOf[Long])
    }
  }

  def fillInJvmCounters(out: mutable.Map[String, Long]) {
    var totalCycles = 0L
    var totalTime = 0L

    ManagementFactory.getGarbageCollectorMXBeans().asScala.foreach { gc =>
      val name = gc.getName.regexSub("""[^\w]""".r) { m => "_" }
      val collectionCount = gc.getCollectionCount
      out += ("jvm_gc_" + name + "_cycles" -> collectionCount)
      val collectionTime = gc.getCollectionTime
      out += ("jvm_gc_" + name + "_msec" -> collectionTime)
      // note, these could be -1 if the collector doesn't have support for it.
      if (collectionCount > 0)
        totalCycles += collectionCount
      if (collectionTime > 0)
        totalTime += gc.getCollectionTime
    }
    out += ("jvm_gc_cycles" -> totalCycles)
    out += ("jvm_gc_msec" -> totalTime)
  }

  /**
   * Attach a new StatsListener to this collection. Additions to metrics will be passed along to
   * each listener.
   */
  def addListener(listener: StatsListener) {
    synchronized {
      listeners += listener
    }
  }

  def addGauge(name: String)(gauge: => Double) {
    gaugeMap.put(name, { () => gauge })
  }

  def clearGauge(name: String) {
    gaugeMap.remove(name)
  }

  def setLabel(name: String, value: String) {
    labelMap.put(name, value)
  }

  def clearLabel(name: String) {
    labelMap.remove(name)
  }

  /**
   * Get a counter `name` from `target`. Creative it with `f` if it does not exist.
   */
  private[this] def getCounter[T](name: String, target: ConcurrentHashMap[String, T], f: => T): T = {
    var counter = target.get(name)
    if (counter == null) {
      target.putIfAbsent(name, f)
      counter = target.get(name)
    }
    counter
  }

  def getCounter(name: String): Counter = getCounter(name, counterMap, newCounter(name))

  override def getFastCounter(name: String): Incrementable = getCounter(name, fastCounterMap, newFastCounter(name))

  def makeCounter(name: String, atomic: AtomicLong): Counter = {
    getCounter(name, counterMap, new Counter(atomic))
  }

  def removeCounter(name: String) {
    counterMap.remove(name)
    fastCounterMap.remove(name)
  }

  protected def newCounter(name: String): Counter = {
    new Counter()
  }

  protected def newFastCounter(name: String): Incrementable = {
    new FastCounter()
  }

  def getMetric(name: String): Metric = {
    var metric = metricMap.get(name)
    if (metric == null) {
      metric = metricMap.putIfAbsent(name, newMetric(name))
      metric = metricMap.get(name)
    }
    metric
  }

  protected def newMetric(name: String): Metric = {
    new Metric()
  }

  def removeMetric(name: String): Option[Metric] = {
    Option(metricMap.remove(name))
  }

  def getLabel(name: String): Option[String] = {
    val value = labelMap.get(name)
    if (value == null) None else Some(value)
  }

  def getGauge(name: String): Option[Double] = {
    val gauge = gaugeMap.get(name)
    if (gauge != null) {
      try {
        Some(gauge())
      } catch { case e =>
        log.error(e, "Gauge error: %s", name)
        None
      }
    } else None
  }

  def getCounters(): mutable.HashMap[String, Long] = {
    val counters = new mutable.HashMap[String, Long]
    if (includeJvmStats) fillInJvmCounters(counters)
    counterMap.asScala.foreach { case (k, v) => counters += k -> v() }
    // Accumulate on collision
    fastCounterMap.asScala.foreach { case (k, v) => counters += k -> (v() + counters.getOrElse(k, 0L)) }
    counters
  }

  def getMetrics(): mutable.HashMap[String, Distribution] = {
    val metrics = new mutable.HashMap[String, Distribution]
    for ((key, metric) <- metricMap.asScala) {
      metrics += (key -> metric())
    }
    metrics
  }

  def getGauges(): mutable.HashMap[String, Double] = {
    val gauges = new mutable.HashMap[String, Double]
    if (includeJvmStats) fillInJvmGauges(gauges)
    for ((key, gauge) <- gaugeMap.asScala) {
      try {
        gauges += (key -> gauge())
      } catch { case e =>
        log.error(e, "Gauge error: %s", key)
      }
    }
    gauges
  }

  def getLabels(): mutable.HashMap[String, String] = {
    new mutable.HashMap[String, String] ++ labelMap.asScala
  }

  def clearAll() {
    counterMap.clear()
    fastCounterMap.clear()
    metricMap.clear()
    gaugeMap.clear()
    labelMap.clear()
    listeners.clear()
  }

  def toJson: String = get().toJson
}

/**
 * Get a StatsCollection specific to this thread.
 * @deprecated use LocalStatsCollection instead
 */
object ThreadLocalStatsCollection {
  private val tl = new ThreadLocal[StatsCollection]() {
    override def initialValue() = new StatsCollection()
  }

  def apply(): StatsCollection = tl.get()
}

/**
 * A StatsCollection that sends counter and metric updates to a set of other (fanout) collections.
 */
class FanoutStatsCollection(others: StatsCollection*) extends StatsCollection {
  override def newCounter(name: String) = new FanoutCounter(others.map { _.getCounter(name) }: _*)
  override def newMetric(name: String) = new FanoutMetric(others.map { _.getMetric(name) }: _*)
}

/**
 * A StatsCollection that sends counter and metric updates to the global `Stats` object as they
 * happen, and can be asked to flush its stats back into another StatsCollection with a prefix.
 *
 * For example, if the prefix is "transaction10", then updating a counter "exceptions" will update
 * this collection and `Stats` simultaneously for "exceptions". When/if the collection is flushed
 * into `Stats` at the end of the transaction, "transaction10.exceptions" will be updated with this
 * collection's "exception" count.
 */
class LocalStatsCollection(collectionName: String) extends FanoutStatsCollection(Stats) {
  import scala.collection.JavaConverters._

  /**
   * Flush this collection's counters and metrics into another StatsCollection, with each counter
   * and metric name prefixed by this collection's name. Counters and metrics in this collection
   * will be cleared out. This is not an atomic operation.
   */
  def flushInto(collection: StatsProvider) {
    (counterMap.asScala ++ fastCounterMap.asScala).foreach { case (name, counter) =>
      collection.getCounter(collectionName + "." + name).incr(counter().toInt)
    }
    metricMap.asScala.foreach { case (name, metric) =>
      collection.getMetric(collectionName + "." + name).add(metric())
    }
    counterMap.clear()
    fastCounterMap.clear()
    metricMap.clear()
  }

  /**
   * Flush this collection's counters and metrics into the global `Stats` object, with each counter
   * and metric name prefixed by this collection's name.
   */
  def flush() {
    flushInto(Stats)
  }
}

/**
 * Get the StatsCollection for your "local state".
 * @see Future, Local (from twitter-util)
 *
 * A LocalStatsCollection is associated with a name, which will be used as the prefix when flushing
 * the local counters and metrics back into the global `Stats`. Because it's stored in a `Local`,
 * a collection can be looked up several times and return the same object, as long as it's in the
 * same "flow of execution" (as defined by `Future`).
 *
 * Counters and metrics that are updated on a LocalStatsCollection are also updated on the global
 * `Stats` object, using the same names. When flushed, these counters and metrics are saved into
 * the global `Stats` with the collection's name as a prefix.
 */
object LocalStatsCollection {
  private val local = new Local[mutable.Map[String, LocalStatsCollection]]()
  local() = new mutable.HashMap[String, LocalStatsCollection]()

  /**
   * Get a named LocalStatsCollection, creating it if it doesn't already exist.
   */
  def apply(name: String): LocalStatsCollection = {
    local().get.getOrElseUpdate(name, new LocalStatsCollection(name))
  }
}

/**
 * Coalesce all events (counters, timings, etc.) that happen in this thread within this
 * transaction, and log them as a single unit at the end. This is useful for logging everything
 * that happens within an HTTP request/response cycle, or similar.
 */
trait TransactionalStatsCollection {
  def apply[T](f: StatsProvider => T): T = {
    val collection = new StatsCollection()
    try {
      f(collection)
    } finally {
      write(collection.get())
    }
  }

  def write(summary: StatsSummary)
}
