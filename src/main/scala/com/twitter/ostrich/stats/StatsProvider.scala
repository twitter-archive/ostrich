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

import scala.collection.{Map, mutable, immutable}
import com.twitter.json.Json
import com.twitter.util.{Duration, Future, Time}
import com.twitter.ostrich.admin.ServiceTracker

/**
 * Immutable summary of counters, metrics, gauges, and labels.
 */
case class StatsSummary(
  counters: Map[String, Long],
  metrics: Map[String, Distribution],
  gauges: Map[String, Double],
  labels: Map[String, String]
) {
  /**
   * Dump a nested map of the stats in this collection, suitable for json output.
   */
  def toMap: Map[String, Any] = {
    val jsonGauges = Map[String, Any]() ++ gauges.map { case (k, v) =>
      if (v.longValue == v) { (k, v.longValue) } else { (k, v) }
    }
    Map(
      "counters" -> counters,
      "metrics" -> metrics,
      "gauges" -> jsonGauges,
      "labels" -> labels
    )
  }

  /**
   * Dump a json-encoded map of the stats in this collection.
   */
  def toJson = {
    Json.build(toMap).toString
  }
}

/**
 * Trait for anything that collects counters, timings, and gauges, and can report them in
 * name/value maps.
 *
 * Many helper methods are provided, with default implementations that just call back into the
 * abstract methods, so a concrete implementation only needs to fill in the abstract methods.
 *
 * To recap the README:
 *
 * - counter: a value that never decreases (like "exceptions" or "completed_transactions")
 * - gauge: a discrete instantaneous value (like "heap_used" or "current_temperature")
 * - metric: a distribution (min, max, median, 99th percentile, ...) like "event_timing"
 * - label: an instantaneous informational string for debugging or status checking
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
    getCounter(name).incr(count)
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
    ServiceTracker.timeRemote(name + "_msec", duration.inMilliseconds.toInt)
    rv
  }

  /**
   * Runs the function f and logs that duration until the future is satisfied, in microseconds, with
   * the given name.
   */
  def timeFutureMicros[T](name: String)(f: Future[T]): Future[T] = {
    val start = Time.now
    f.respond { _ =>
      addMetric(name + "_usec", start.sinceNow.inMicroseconds.toInt)
      ServiceTracker.timeRemote(name + "_usec", start.sinceNow.inMicroseconds.toInt)
    }
    f
  }

  /**
   * Runs the function f and logs that duration until the future is satisfied, in milliseconds, with
   * the given name.
   */
  def timeFutureMillis[T](name: String)(f: Future[T]): Future[T] = {
    val start = Time.now
    f.respond { _ =>
      addMetric(name + "_msec", start.sinceNow.inMilliseconds.toInt)
      ServiceTracker.timeRemote(name + "_msec", start.sinceNow.inMilliseconds.toInt)
    }
    f
  }

  /**
   * Runs the function f and logs that duration until the future is satisfied, in nanoseconds, with
   * the given name.
   */
  def timeFutureNanos[T](name: String)(f: Future[T]): Future[T] = {
    val start = Time.now
    f.respond { _ =>
      addMetric(name + "_nsec", start.sinceNow.inNanoseconds.toInt)
      ServiceTracker.timeRemote(name + "_nsec", start.sinceNow.inNanoseconds.toInt)
    }
    f
  }

  /**
   * Runs the function f and logs that duration, in microseconds, with the given name.
   */
  def timeMicros[T](name: String)(f: => T): T = {
    val (rv, duration) = Duration.inNanoseconds(f)
    addMetric(name + "_usec", duration.inMicroseconds.toInt)
    ServiceTracker.timeRemote(name + "_usec", duration.inMicroseconds.toInt)
    rv
  }

  /**
   * Runs the function f and logs that duration, in nanoseconds, with the given name.
   */
  def timeNanos[T](name: String)(f: => T): T = {
    val (rv, duration) = Duration.inNanoseconds(f)
    addMetric(name + "_nsec", duration.inNanoseconds.toInt)
    ServiceTracker.timeRemote(name + "_nsec", duration.inNanoseconds.toInt)
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
