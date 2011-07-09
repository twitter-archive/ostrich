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
import com.twitter.conversions.time._
import com.twitter.json.{Json, JsonSerializable}
import com.twitter.util.Duration
import admin.{ServiceTracker, PeriodicBackgroundProcess}

object StatsListener {
  val listeners = new ConcurrentHashMap[(Duration, StatsCollection), StatsListener]

  // make sure there's always at least a 1-minute collector.
  // XXX clearAll invalidates this -- is this really useful if it will be created on first request?
  listeners.put((1.minute, Stats), new LatchedStatsListener(Stats, 1.minute, false))

  def clearAll() {
    listeners.clear()
  }

  /**
   * Get a StatsListener that's attached to a specified stats collection and tracks periodic stats
   * over the given duration, creating it if it doesn't already exist.
   */
  def apply(period: Duration, collection: StatsCollection): StatsListener = {
    Option {
      listeners.get((period, collection))
    }.getOrElse {
      val x = new LatchedStatsListener(collection, period, false)
      listeners.putIfAbsent((period, collection), x)
      listeners.get((period, collection))
    }
  }

  /**
   * Get a StatsListener that's attached to the global stats collection and tracks periodic stats
   * over the given duration, creating it if it doesn't already exist.
   */
  def apply(period: Duration): StatsListener = apply(period, Stats)
}

/**
 * Attaches to a StatsCollection and reports on all the counters, metrics, gauges, and labels.
 * Each report resets state, so counters are reported as deltas, and metrics distributions are
 * only tracked since the last report.
 */
class StatsListener(collection: StatsCollection, startClean: Boolean) {
  def this(collection: StatsCollection) = this(collection, true)

  private val lastCounterMap = new mutable.HashMap[String, Long]()
  private val lastMetricMap = new mutable.HashMap[String, Distribution]()

  collection.addListener(this)
  if (startClean) {
    collection.getCounters().foreach { case (key, value) =>
      lastCounterMap(key) = value
    }
    collection.getMetrics().foreach { case (key, value) =>
      lastMetricMap(key) = value
    }
  }

  def getCounters(): Map[String, Long] = synchronized {
    val deltas = new mutable.HashMap[String, Long]
    for ((key, newValue) <- collection.getCounters()) {
      deltas(key) = Stats.delta(lastCounterMap.getOrElse(key, 0), newValue)
      lastCounterMap(key) = newValue
    }
    deltas
  }

  def getMetrics(): Map[String, Distribution] = synchronized {
    val deltas = new mutable.HashMap[String, Distribution]
    for ((key, newValue) <- collection.getMetrics()) {
      deltas(key) = newValue - lastMetricMap.getOrElse(key, new Distribution())
      lastMetricMap(key) = newValue
    }
    deltas
  }

  def get(): StatsSummary = {
    StatsSummary(getCounters(), getMetrics(), collection.getGauges(), collection.getLabels())
  }
}

/**
 * A StatsListener that cycles over a given period, and once each period, grabs a snapshot of the
 * given StatsCollection and computes deltas since the previous timeslice. For example, for a
 * one-minute period, it grabs a snapshot of stats at the top of each minute, and for the rest of
 * the minute, reports these "latched" stats.
 */
class LatchedStatsListener(collection: StatsCollection, period: Duration, startClean: Boolean)
extends StatsListener(collection, startClean) {
  def this(collection: StatsCollection, period: Duration) = this(collection, period, true)

  @volatile private var counters: Map[String, Long] = Map()
  @volatile private var metrics: Map[String, Distribution] = Map()
  nextLatch()

  override def getCounters() = counters
  override def getMetrics() = metrics

  def nextLatch() {
    counters = super.getCounters()
    metrics = super.getMetrics()
  }

  // lazy to allow a subclass to override it
  lazy val service = new PeriodicBackgroundProcess("LatchedStatsListener", period) {
    def periodic() {
      nextLatch()
    }
  }

  ServiceTracker.register(service)
}
