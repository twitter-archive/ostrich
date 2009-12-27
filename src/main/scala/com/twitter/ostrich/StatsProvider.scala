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

import scala.collection.{Map, mutable, immutable}


/**
 * Trait for anything that collects counters and timings, can report them in name/value maps,
 * and can reset those maps when asked.
 */
trait StatsProvider {
  /**
   * Runs the function f and logs that duration, in milliseconds, with the given name.
   */
  def time[T](name: String)(f: => T): T = {
    val (rv, msec) = Stats.duration(f)
    addTiming(name, msec.toInt)
    rv
  }

  /**
   * Runs the function f and logs that duration, in nanoseconds, with the given name.
   *
   * When using nanoseconds, be sure to encode your field with that fact. Consider
   * using the suffix `_ns` in your field.
   */
  def timeNanos[T](name: String)(f: => T): T = {
    val (rv, nsec) = Stats.durationNanos(f)
    addTiming(name, nsec.toInt)
    rv
  }

  /**
   * Stores a timing (in arbirtrary units). Returns the total number of timings stored so far.
   */
  def addTiming(name: String, duration: Int): Long

  /**
   * Stores a set of summarized timings.
   */
  def addTiming(name: String, timingStat: TimingStat): Long

  /**
   * Increments a count in the stats, returning the new value.
   */
  def incr(name: String, count: Int): Long

  /**
   * Increments a count in the stats, returning the new value.
   */
  def incr(name: String): Long = incr(name, 1)

  /**
   * Returns a map of counters and their current values.
   * @param reset whether or not to reset the counters after reading them
   */
  def getCounterStats(reset: Boolean): Map[String, Long]

  /**
   * Returns a map of counters and their current values.
   */
  def getCounterStats(): Map[String, Long] = getCounterStats(false)

  /**
   * Returns a map of timings.
   @ param reset whether or not to reset the timing stats after reading them
   */
  def getTimingStats(reset: Boolean): Map[String, TimingStat]

  /**
   * Returns a map of timings.
   */
  def getTimingStats(): Map[String, TimingStat] = getTimingStats(false)

  /**
   * Return a nested map containing counters, timings, gauges, and the JVM stats, suitable for
   * encoding into JSON or XML, or flattening into text.
   */
  def stats(reset: Boolean): Map[String, Map[String, Any]] = {
    immutable.Map("counters" -> getCounterStats(reset), "timings" -> getTimingStats(reset))
  }

  /**
   * Reset all collected stats and erase the history.
   */
  def clearAll()
}


/**
 * A StatsProvider that doesn't actually save or report anything.
 */
object DevNullStats extends StatsProvider {
  def addTiming(name: String, duration: Int) = 0
  def addTiming(name: String, timingStat: TimingStat) = 0
  def incr(name: String, count: Int): Long = count.toLong
  def getCounterStats(reset: Boolean) = immutable.Map.empty
  def getTimingStats(reset: Boolean) = immutable.Map.empty
  def clearAll() = ()
}
