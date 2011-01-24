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

import scala.collection.mutable
import com.twitter.logging.Logger
import com.twitter.util.Duration

// TODO(benjy): Do we really need four different classes for interaction with a log??
// (LogEntry, LogReporter, PerThreadStats and StatsLogger!!)

// TODO(benjy): This needs to be generalized so that JsonStatsLogger can be implemented in terms of StatsLogger.
// The specifics below belong to the W3C world.

/**
 * Log all collected stats to a java logger at a regular interval.
 */
class StatsLogger(val reporter: LogReporter, val frequency: Duration, collection: StatsCollection)
extends PeriodicBackgroundProcess("StatsLogger", frequency) {
  def this(reporter: LogReporter, frequency: Duration) = this(reporter, frequency, Stats)

  val listener = new StatsListener(collection)

  def periodic() {
    val report = new mutable.HashMap[String, Any]
    val summary = listener.get()

    summary.counters foreach { case (key, value) => report(key) = value }
    summary.gauges foreach { case (key, value) => report(key) = value }

    summary.metrics foreach { case (key, distribution) =>
      report(key + "_count") = distribution.count
      report(key + "_min") = distribution.minimum
      report(key + "_max") = distribution.maximum
      report(key + "_avg") = distribution.average.toLong
    }

    reporter.report(report)
  }
}
