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

import scala.collection.{Map, mutable, immutable}
import com.twitter.util.Duration

case class StatsSummary(
  counters: Map[String, Long],
  metrics: Map[String, Distribution],
  gauges: Map[String, Double],
  labels: Map[String, String]
)

/**
 * Trait for anything that collects counters, timings, and gauges, and can report them in
 * name/value maps.
 */
trait StatsProvider {
  /**
   * Adds a value to a named metric, which tracks min, max, mean, and a histogram.
   */
  def addMetric(name: String, value: Int) {
    getMetric(name).add(value)
  }

  /**
   * Adds a set of values to a named metric. Effectively the incoming distribution is merged with
   * the named metric.
   */
  def addMetric(name: String, distribution: Distribution) {
    getMetric(name).add(distribution)
  }

  /**
   * Increments a counter, returning the new value.
   */
  def incr(name: String, count: Int): Long = {
    getCounter(name).value.addAndGet(count)
  }

  /**
   * Increments a counter by one, returning the new value.
   */
  def incr(name: String): Long = incr(name, 1)

  /**
   * Add a gauge function, which is used to sample instantaneous values.
   */
  def addGauge(name: String)(gauge: => Double)

  /**
   * Set a gauge to a specific value. This overwrites any previous value or function.
   */
  def setGauge(name: String, value: Double) {
    addGauge(name)(value)
  }

  /**
   * Remove a gauge from the provided stats.
   */
  def clearGauge(name: String)

  /**
   * Set a label to a string.
   */
  def setLabel(name: String, value: String)

  /**
   * Clear an existing label.
   */
  def clearLabel(name: String)

  /**
   * Get the Counter object representing a named counter.
   */
  def getCounter(name: String): Counter

  /**
   * Get the Metric object representing a named metric.
   */
  def getMetric(name: String): Metric

  /**
   * Get the current value of a named gauge.
   */
  def getGauge(name: String): Option[Double]

  /**
   * Get the current value of a named label, if it exists.
   */
  def getLabel(name: String): Option[String]

  /**
   * Summarize all the counters in this collection.
   */
  def getCounters(): Map[String, Long]

  /**
   * Summarize all the metrics in this collection.
   */
  def getMetrics(): Map[String, Distribution]

  /**
   * Summarize all the gauges in this collection.
   */
  def getGauges(): Map[String, Double]

  /**
   * Summarize all the labels in this collection.
   */
  def getLabels(): Map[String, String]

  /**
   * Summarize all the counters, metrics, gauges, and labels in this collection.
   */
  def get(): StatsSummary = StatsSummary(getCounters(), getMetrics(), getGauges(), getLabels())

  /**
   * Reset all collected stats and erase the history.
   * Probably only useful for unit tests.
   */
  def clearAll()

  /**
   * Runs the function f and logs that duration, in milliseconds, with the given name.
   */
  def time[T](name: String)(f: => T): T = {
    val (rv, duration) = Duration.inMilliseconds(f)
    addMetric(name + "_msec", duration.inMilliseconds.toInt)
    rv
  }

  /**
   * Runs the function f and logs that duration, in microseconds, with the given name.
   */
  def timeMicros[T](name: String)(f: => T): T = {
    val (rv, duration) = Duration.inNanoseconds(f)
    addMetric(name + "_usec", duration.inMicroseconds.toInt)
    rv
  }

  /**
   * Runs the function f and logs that duration, in nanoseconds, with the given name.
   */
  def timeNanos[T](name: String)(f: => T): T = {
    val (rv, duration) = Duration.inNanoseconds(f)
    addMetric(name + "_nsec", duration.inNanoseconds.toInt)
    rv
  }
}

/**
 * A StatsProvider that doesn't actually save or report anything.
 */
object DevNullStats extends StatsProvider {
  def addGauge(name: String)(gauge: => Double) = ()
  def clearGauge(name: String) = ()
  def setLabel(name: String, value: String) = ()
  def clearLabel(name: String) = ()
  def getCounter(name: String) = new Counter()
  def getMetric(name: String) = new Metric()
  def getGauge(name: String) = None
  def getLabel(name: String) = None
  def getCounters() = Map.empty
  def getMetrics() = Map.empty
  def getGauges() = Map.empty
  def getLabels() = Map.empty
  def clearAll() = ()
}
