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
package w3c

import scala.collection.mutable
import com.twitter.conversions.time._
import com.twitter.logging.Logger
import com.twitter.util.{Duration, Time}
import stats._

/**
 * Log all collected stats as "w3c-style" lines to a java logger at a regular interval.
 */
class W3CStatsLogger(val logger: Logger, val frequency: Duration, includeJvmStats: Boolean)
extends PeriodicBackgroundProcess("W3CStatsLogger", frequency) {
  def this(logger: Logger, frequency: Duration) = this(logger, frequency, true)

  val reporter = new W3CReporter(logger)
  val statsReporter = new StatsReporter(Stats)

  def periodic() {
    val report = new mutable.HashMap[String, Any]

    if (includeJvmStats) {
      Stats.getJvmStats() foreach { case (key, value) => report("jvm_" + key) = value }
    }

    Stats.getCounters() foreach { case (key, value) => report(key) = value }
    Stats.getGauges() foreach { case (key, value) => report(key) = value }

    Stats.getMetrics() foreach { case (key, distribution) =>
      report(key + "_count") = distribution.count
      report(key + "_min") = distribution.minimum
      report(key + "_max") = distribution.maximum
      report(key + "_avg") = distribution.average
    }

    reporter.report(report)
  }
}
