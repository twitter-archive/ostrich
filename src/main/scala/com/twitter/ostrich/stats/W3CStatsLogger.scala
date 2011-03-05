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

import com.twitter.logging.Logger
import com.twitter.util.Duration
import admin.PeriodicBackgroundProcess

/**
 * Log all collected w3c stats at a regular interval.
 */
class W3CStatsLogger(val logger: Logger, frequency: Duration, collection: StatsCollection)
extends PeriodicBackgroundProcess("W3CStatsLogger", frequency) {
  def this(logger: Logger, frequency: Duration) = this(logger, frequency, Stats)

  val w3cStats = new W3CStats(logger, Array(), true)
  val listener = new StatsListener(collection)

  def periodic() {
    w3cStats.write(listener.get())
  }
}
