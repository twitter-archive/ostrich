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

import java.lang.management._
import java.util.concurrent.ConcurrentHashMap
import scala.collection.{JavaConversions, Map, mutable, immutable}
import com.twitter.json.{Json, JsonSerializable}
/**
 * Concrete StatsProvider that tracks counters and timings.
 */
class StatsCollection extends StatsProvider with JsonSerializable {
  private val counterMap = new ConcurrentHashMap[String, Counter]()
  private val metricMap = new ConcurrentHashMap[String, FanoutMetric]()
  private val gaugeMap = new ConcurrentHashMap[String, () => Double]()
  private val labelMap = new ConcurrentHashMap[String, String]()

  private val listeners = new mutable.ListBuffer[StatsListener]

  /** Set this to true to have the collection fill in a set of automatic gauges from the JVM. */
  var includeJvmStats = false

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

    val gcs = JavaConversions.asScalaIterable(
      ManagementFactory.getGarbageCollectorMXBeans())
    var gcCount = 0L
    var gcTimes = 0L
    for (gc <- gcs) {
      val prefix = "jvm_gc_" + gc.getName()
      out += (prefix + "_count" -> gc.getCollectionCount().toLong)
      out += (prefix + "_time" -> gc.getCollectionTime().toLong)
      out += (prefix + "_time_avg" ->
              (gc.getCollectionTime() / (gc.getCollectionCount() + 1)).toLong)

      gcCount += gc.getCollectionCount().toLong
      gcTimes += gc.getCollectionTime().toLong
    }
    out += ("jvm_gc_all_count" -> gcCount)
    out += ("jvm_gc_all_time" -> gcTimes)
    out += ("jvm_gc_all_time_avg" -> (gcCount / (gcTimes + 1)))

    val threads = ManagementFactory.getThreadMXBean()
    out += ("jvm_thread_daemon_count" -> threads.getDaemonThreadCount().toLong)
    out += ("jvm_thread_count" -> threads.getThreadCount().toLong)
    out += ("jvm_thread_peak_count" -> threads.getPeakThreadCount().toLong)

    val runtime = ManagementFactory.getRuntimeMXBean()
    out += ("jvm_start_time" -> runtime.getStartTime())
    out += ("jvm_uptime" -> runtime.getUptime())

    val os = ManagementFactory.getOperatingSystemMXBean()
    out += ("jvm_num_cpus" -> os.getAvailableProcessors().toLong)
    out += ("jvm_load_avg" -> os.getAvailableProcessors().toLong)

    out
  }

  def addListener(listener: StatsListener) {
    synchronized {
      listeners += listener
      for ((key, metric) <- JavaConversions.asScalaMap(metricMap)) {
        metric.addFanout(listener.getMetric(key))
      }
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

  def getCounter(name: String) = {
    var counter = counterMap.get(name)
    while (counter == null) {
      counter = counterMap.putIfAbsent(name, new Counter())
      counter = counterMap.get(name)
    }
    counter
  }

  def getMetric(name: String) = {
    var metric = metricMap.get(name)
    if (metric == null) {
      metric = new FanoutMetric()
      synchronized {
        listeners.foreach { listener => metric.addFanout(listener.getMetric(name)) }
      }
      metricMap.putIfAbsent(name, metric)
      metric = metricMap.get(name)
    }
    metric
  }

  def getLabel(name: String) = {
    val value = labelMap.get(name)
    if (value == null) None else Some(value)
  }

  def getGauge(name: String) = {
    val gauge = gaugeMap.get(name)
    if (gauge == null) None else Some(gauge())
  }

  def getCounters() = {
    val counters = new mutable.HashMap[String, Long]
    for ((key, counter) <- JavaConversions.asScalaMap(counterMap)) {
      counters += (key -> counter())
    }
    counters
  }

  def getMetrics() = {
    val metrics = new mutable.HashMap[String, Distribution]
    for ((key, metric) <- JavaConversions.asScalaMap(metricMap)) {
      metrics += (key -> metric(true))
    }
    metrics
  }

  def getGauges() = {
    val gauges = new mutable.HashMap[String, Double]
    if (includeJvmStats) fillInJvmGauges(gauges)
    for ((key, gauge) <- JavaConversions.asScalaMap(gaugeMap)) {
      gauges += (key -> gauge())
    }
    gauges
  }

  def getLabels() = {
    new mutable.HashMap[String, String] ++ JavaConversions.asScalaMap(labelMap)
  }

  def clearAll() {
    counterMap.clear()
    metricMap.clear()
    gaugeMap.clear()
    labelMap.clear()
    listeners.clear()
  }

  def toMap: Map[String, Any] = {
    val gauges = Map[String, Any]() ++ getGauges().map { case (k, v) =>
      if (v.longValue == v) { (k, v.longValue) } else { (k, v) }
    }
    Map(
      "counters" -> getCounters(),
      "metrics" -> getMetrics(),
      "gauges" -> gauges,
      "labels" -> getLabels()
    )
  }

  def toJson = {
    Json.build(toMap).toString
  }
}

/**
 * Get a StatsCollection specific to this thread.
 */
object ThreadLocalStatsCollection {
  private val tl = new ThreadLocal[StatsCollection]() {
    override def initialValue() = new StatsCollection()
  }

  def apply(): StatsCollection = tl.get()
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
