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

package com.twitter.ostrich
package stats

import java.util.concurrent.ConcurrentHashMap
import scala.collection.{JavaConversions, Map, mutable, immutable}
import com.twitter.json.{Json, JsonSerializable}

class StatsReporter(collection: StatsCollection) {
  private val metricMap = new ConcurrentHashMap[String, Metric]()
  private val lastCounterMap = new mutable.HashMap[String, Long]()

  collection.addReporter(this)
  collection.getCounters.foreach { case (key, value) =>
    lastCounterMap(key) = value
  }

  def getMetric(name: String) = {
    var metric = metricMap.get(name)
    while (metric == null) {
      metric = metricMap.putIfAbsent(name, new Metric())
      metric = metricMap.get(name)
    }
    metric
  }

  final def delta(oldValue: Long, newValue: Long): Long = {
    if (oldValue <= newValue) {
      newValue - oldValue
    } else {
      0
    }
  }

  def getCounters() = synchronized {
    val deltas = new mutable.HashMap[String, Long]
    for ((key, newValue) <- collection.getCounters()) {
      deltas(key) = delta(lastCounterMap.getOrElse(key, 0), newValue)
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
    StatsSummary(getCounters(), getMetrics(), collection.getGauges())
  }
}
