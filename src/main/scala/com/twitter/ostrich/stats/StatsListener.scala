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
  val listeners = new ConcurrentHashMap[(String, StatsCollection), StatsListener]

  def clearAll() {
    listeners.clear()
  }

  def getOrRegister(id: String, collection: StatsCollection, listener: => StatsListener) = {
    val key = (id, collection)
    Option {
      listeners.get(key)
    }.getOrElse {
      listeners.putIfAbsent(key, listener)
      listeners.get(key)
    }
  }

  /**
   * Get a StatsListener that's attached to a specified stats collection tracked by name,
   * creating it if it doesn't already exist.
   */
  def apply(name: String, collection: StatsCollection): StatsListener = {
    getOrRegister("ns:%s".format(name), collection, new StatsListener(collection, false))
  }

  /**
   * Get a StatsListener that's attached to a specified stats collection and tracks periodic stats
   * over the given duration, creating it if it doesn't already exist.
   */
  def apply(period: Duration, collection: StatsCollection): StatsListener = {
    getOrRegister("period:%d".format(period.inMillis), collection,
      new LatchedStatsListener(collection, period, false))
  }

  /**
   * Get a StatsListener that's attached to a specified stats collection and tracks periodic stats
   * over the given duration, creating it if it doesn't already exist.
   */
  def apply(period: Duration, collection: StatsCollection, filters: List[String]): StatsListener = {
    getOrRegister("period:%d".format(period.inMillis), collection,
      new LatchedStatsListener(collection, period, false, filters))
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
class StatsListener(collection: StatsCollection, startClean: Boolean, filters: List[String]) {
  def this(collection: StatsCollection, startClean: Boolean) = this(collection, startClean, Nil)
  def this(collection: StatsCollection) = this(collection, true, Nil)

  private val lastCounterMap = new mutable.HashMap[String, Long]()
  private val lastMetricMap = new mutable.HashMap[String, Distribution]()

  private val filterRegex = filters.mkString("(", ")|(", ")").r

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

  def getGauges(): Map[String, Double] = collection.getGauges()

  def getLabels(): Map[String, String] = collection.getLabels()

  def getMetrics(): Map[String, Distribution] = synchronized {
    val deltas = new mutable.HashMap[String, Distribution]
    for ((key, newValue) <- collection.getMetrics()) {
      deltas(key) = newValue - lastMetricMap.getOrElse(key, new Distribution())
      lastMetricMap(key) = newValue
    }
    deltas
  }

  def get(): StatsSummary = StatsSummary(getCounters(), getMetrics(), getGauges(), getLabels())

  def get(filtered: Boolean): StatsSummary = if (filtered) getFiltered() else get()

  def getFiltered(): StatsSummary = {
    get().filterOut(filterRegex)
  }
}

/**
 * A StatsListener that cycles over a given period, and once each period, grabs a snapshot of the
 * given StatsCollection and computes deltas since the previous timeslice. For example, for a
 * one-minute period, it grabs a snapshot of stats at the top of each minute, and for the rest of
 * the minute, reports these "latched" stats.
 */
class LatchedStatsListener(
  collection: StatsCollection,
  period: Duration,
  startClean: Boolean,
  filters: List[String]
) extends StatsListener(collection, startClean, filters) {
  def this(collection: StatsCollection, period: Duration, startClean: Boolean) = this(collection, period, startClean, Nil)
  def this(collection: StatsCollection, period: Duration) = this(collection, period, true, Nil)

  @volatile private var counters: Map[String, Long] = Map()
  @volatile private var gauges: Map[String, Double] = Map()
  @volatile private var labels: Map[String, String] = Map()
  @volatile private var metrics: Map[String, Distribution] = Map()
  nextLatch()

  override def getCounters() = counters
  override def getGauges() = gauges
  override def getLabels() = labels
  override def getMetrics() = metrics

  def nextLatch() {
    counters = super.getCounters()
    labels = super.getLabels()
    metrics = super.getMetrics()
    // do gauges last since they might be constructed using the others.
    gauges = super.getGauges()
  }

  // FIXME this would be more efficient as a Timer for all LatchedStatsListeners?
  // lazy to allow a subclass to override it
  lazy val service = new PeriodicBackgroundProcess("LatchedStatsListener", period) {
    def periodic() {
      nextLatch()
    }
  }

  ServiceTracker.register(service)
  service.start()
}
