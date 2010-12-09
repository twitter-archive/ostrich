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

package com.twitter.ostrich

import java.lang.management._
import java.util.concurrent.atomic.AtomicLong
import scala.collection.{Map, mutable, immutable}
import com.twitter.json.Json
import com.twitter.logging.Logger
import com.twitter.Time

/**
 * Basic StatsProvider gathering object that returns performance data for the application.
 */
object Stats extends StatsProvider {
  val log = Logger.get(getClass.getName)

  /**
   * A gauge has an instantaneous value (like memory usage) and is polled whenever stats
   * are collected.
   */
  trait Gauge extends ((Boolean) => Double)

  private val gaugeMap = new mutable.HashMap[String, Gauge]()
  private val timingSourceList = new mutable.ListBuffer[() => Map[String, TimingStat]]()

  private val collection = new StatsCollection
  private val forkedCollections = new mutable.ListBuffer[StatsCollection]

  def addTiming(name: String, duration: Int): Long = {
    forkedCollections.foreach { _.addTiming(name, duration) }
    collection.addTiming(name, duration)
  }

  def addTiming(name: String, timingStat: TimingStat): Long = {
    forkedCollections.foreach { _.addTiming(name, timingStat) }
    collection.addTiming(name, timingStat)
  }

  def setGauge(name: String, value: Double) = gaugeMap.synchronized {
    gaugeMap += name -> new Gauge { def apply(reset: Boolean) = value }
  }

  def clearGauge(name: String) = gaugeMap.synchronized {
    gaugeMap -= name
  }

  def incr(name: String, count: Int): Long = {
    forkedCollections.foreach { _.incr(name, count) }
    collection.incr(name, count)
  }

  def getCounterStats(reset: Boolean) = collection.getCounterStats(reset)

  def getTimingStats(reset: Boolean) = {
    val stats = new mutable.HashMap[String, TimingStat]()
    stats ++= collection.getTimingStats(reset)
    timingSourceList.foreach { stats ++= _() }
    stats
  }

  // FIXME: should ditch this API.
  def getCounter(name: String): Counter = {
    new Counter {
      final override def incr() = incr(1)
      final override def incr(n: Int) = Stats.incr(name)
      final override def apply(reset: Boolean) = collection.getCounter(name).apply(reset)
      final override def update(n: Long) = {
        collection.getCounter(name).update(n)
        forkedCollections.foreach { _.getCounter(name).update(n) }
      }
    }
  }

  // FIXME: should ditch this API. this version is broken for forked collections.
  def getTiming(name: String): Timing = {
    collection.getTiming(name)
  }

  override def clearAll() {
    collection.clearAll()
    forkedCollections.foreach { _.clearAll() }
    forkedCollections.clear()
    gaugeMap.synchronized { gaugeMap.clear() }
    timingSourceList.clear()
  }

  /**
   * Fork a StatsCollection (of counters and timings) off the main `Stats` object and return it.
   * The new collection will be updated whenever the primary `Stats` object is, but calls to
   * `getCounterStats` or `getTimingStats` with `reset=true` will not have cross effects. That is,
   * reseting `Stats` will not clear out any forked collections, and vice versa.
   *
   * This method is not thread-safe. Create forked collections before going multi-threaded.
   */
  def fork(): StatsCollection = {
    val x = new StatsCollection
    forkedCollections += x
    x
  }

  /**
   * Register an external source of timings. When timing stats are fetched for reporting, each
   * registered source will be called, and the resulting map will be merged into the report.
   *
   * This method is not thread-safe. Register timing sources before going multi-threaded.
   */
  def registerTimingSource(f: () => Map[String, TimingStat]) = {
    timingSourceList += f
  }

  /**
   * Create a gauge with the given name.
   */
  def makeGauge(name: String)(gauge: => Double): Unit = gaugeMap.synchronized {
    gaugeMap += (name -> new Gauge { def apply(reset: Boolean) = gauge })
  }

  def makeDerivativeGauge(name: String, nomCounter: Counter, denomCounter: Counter): Unit = {
    val g = new Gauge {
      var lastNom: Long = 0
      var lastDenom: Long = 0

      def apply(reset: Boolean) = {
        val nom = nomCounter(false)
        val denom = denomCounter(false)
        val deltaNom = nom - lastNom
        val deltaDenom = denom - lastDenom
        if (reset) {
          lastNom = nom
          lastDenom = denom
        }
        if (deltaDenom == 0) 0.0 else deltaNom * 1.0 / deltaDenom
      }
    }
    gaugeMap.synchronized {
      gaugeMap += (name -> g)
    }
  }

  /**
   * Returns how long it took, in milliseconds, to run the function f.
   */
  def duration[T](f: => T): (T, Long) = {
    val start = Time.now
    val rv = f
    val duration = Time.now - start
    (rv, duration.inMilliseconds)
  }

  /**
   * Returns how long it took, in microseconds, to run the function f.
   */
  def durationMicros[T](f: => T): (T, Long) = {
    val (rv, duration) = durationNanos(f)
    (rv, duration / 1000)
  }

  /**
   * Returns how long it took, in nanoseconds, to run the function f.
   */
  def durationNanos[T](f: => T): (T, Long) = {
    val start = System.nanoTime
    val rv = f
    val duration = System.nanoTime - start
    (rv, duration)
  }

  /**
   * Returns a Map[String, Long] of JVM stats.
   */
  def getJvmStats(): Map[String, Long] = {
    val out = new mutable.HashMap[String, Long]
    val mem = ManagementFactory.getMemoryMXBean()

    val heap = mem.getHeapMemoryUsage()
    out += ("heap_committed" -> heap.getCommitted())
    out += ("heap_max" -> heap.getMax())
    out += ("heap_used" -> heap.getUsed())

    val nonheap = mem.getNonHeapMemoryUsage()
    out += ("nonheap_committed" -> nonheap.getCommitted())
    out += ("nonheap_max" -> nonheap.getMax())
    out += ("nonheap_used" -> nonheap.getUsed())

    val threads = ManagementFactory.getThreadMXBean()
    out += ("thread_daemon_count" -> threads.getDaemonThreadCount().toLong)
    out += ("thread_count" -> threads.getThreadCount().toLong)
    out += ("thread_peak_count" -> threads.getPeakThreadCount().toLong)

    val runtime = ManagementFactory.getRuntimeMXBean()
    out += ("start_time" -> runtime.getStartTime())
    out += ("uptime" -> runtime.getUptime())

    val os = ManagementFactory.getOperatingSystemMXBean()
    out += ("num_cpus" -> os.getAvailableProcessors().toLong)

    out
  }

  /**
   * Returns a Map[String, Double] of current gauge readings.
   */
  def getGaugeStats(reset: Boolean): Map[String, Double] = {
    immutable.Map(gaugeMap.map(x => (x._1, x._2(reset))).toList: _*)
  }

  def getGauge(name: String): Option[Double] = {
    gaugeMap.get(name).flatMap{ gauge => Some(gauge(false)) }
  }

  override def stats(reset: Boolean): Map[String, Map[String, Any]] = {
    val out = super.stats(reset) ++ immutable.Map("jvm" -> Stats.getJvmStats(), "gauges" -> getGaugeStats(reset))
    immutable.Map(out.toSeq: _*)
  }
}
