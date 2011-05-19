/*
 * Copyright 2011 Twitter, Inc.
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

import java.util.concurrent.ConcurrentHashMap
import scala.collection.{JavaConversions, Map, mutable, immutable}
import com.twitter.json.{Json, JsonSerializable}

object StatsListener {
  val listeners = new mutable.HashMap[String, StatsListener]

  /**
   * Get a named StatsListener that's attached to a specified stats collection, creating it if it
   * doesn't already exist.
   */
  def apply(name: String, collection: StatsCollection): StatsListener = {
    listeners.getOrElseUpdate(name, new StatsListener(collection, false))
  }

  /**
   * Get a named StatsListener that's attached to the global stats collection, creating it if it
   * doesn't already exist.
   */
  def apply(name: String): StatsListener = apply(name, Stats)
}

/**
 * Attaches to a StatsCollection and reports on all the counters, metrics, gauges, and labels.
 * Each report resets state, so counters are reported as deltas, and metrics distributions are
 * only tracked since the last report.
 */
class StatsListener(collection: StatsCollection, startClean: Boolean) {
  def this(collection: StatsCollection) = this(collection, true)

  private val metricMap = new ConcurrentHashMap[String, Metric]()
  private val lastCounterMap = new mutable.HashMap[String, Long]()

  collection.addListener(this)
  if (startClean) {
    collection.getCounters.foreach { case (key, value) =>
      lastCounterMap(key) = value
    }
  }

  def getMetric(name: String) = {
    var metric = metricMap.get(name)
    while (metric == null) {
      metric = metricMap.putIfAbsent(name, new Metric())
      metric = metricMap.get(name)
    }
    metric
  }

  def getCounters() = synchronized {
    val deltas = new mutable.HashMap[String, Long]
    for ((key, newValue) <- collection.getCounters()) {
      deltas(key) = Stats.delta(lastCounterMap.getOrElse(key, 0), newValue)
      lastCounterMap(key) = newValue
    }
    deltas
  }

  def getMetrics() = {
    val metrics = new mutable.HashMap[String, Distribution]
    for ((key, metric) <- JavaConversions.asScalaMap(metricMap)) {
      metrics += (key -> metric(true))
    }
    metrics
  }

  def get(): StatsSummary = {
    StatsSummary(getCounters(), getMetrics(), collection.getGauges(), collection.getLabels())
  }
}
