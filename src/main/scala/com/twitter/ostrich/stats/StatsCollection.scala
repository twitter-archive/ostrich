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
package stats

import java.lang.management._
import java.util.concurrent.ConcurrentHashMap
import scala.collection.{JavaConversions, Map, mutable, immutable}

class StatsReporter(collection: StatsCollection) {
  private val metricMap = new ConcurrentHashMap[String, Metric]()

  collection.addReporter(this)

  def getMetric(name: String) = {
    var metric = metricMap.get(name)
    while (metric == null) {
      metric = metricMap.putIfAbsent(name, new Metric())
      metric = metricMap.get(name)
    }
    metric
  }
}

/**
 * Concrete StatsProvider that tracks counters and timings.
 */
class StatsCollection extends StatsProvider {
  private val counterMap = new ConcurrentHashMap[String, Counter]()
  private val metricMap = new ConcurrentHashMap[String, FanoutMetric]()
  private val gaugeMap = new ConcurrentHashMap[String, () => Double]()

  private val reporters = new mutable.ListBuffer[StatsReporter]

  /** Set this to true to have the collection fill in a set of automatic gauges from the JVM. */
  var includeJvmStats = false

  def fillInJvmGauges(out: mutable.Map[String, Double]) {
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

  def addReporter(reporter: StatsReporter) {
    synchronized {
      reporters += reporter
      for ((key, metric) <- JavaConversions.asScalaMap(metricMap)) {
        metric.addFanout(reporter.getMetric(key))
      }
    }
  }

  def addGauge(name: String, gauge: => Double) {
    gaugeMap.put(name, { () => gauge })
  }

  def clearGauge(name: String) {
    gaugeMap.remove(name)
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
        reporters.foreach { reporter => metric.addFanout(reporter.getMetric(name)) }
      }
      metricMap.putIfAbsent(name, metric)
      metric = metricMap.get(name)
    }
    metric
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

  def clearAll() {
    counterMap.clear()
    metricMap.clear()
    gaugeMap.clear()
    reporters.clear()
  }
}
