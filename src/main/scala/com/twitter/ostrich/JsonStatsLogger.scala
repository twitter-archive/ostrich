/*
 * Copyright 2010 Twitter, Inc.
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

import scala.collection.{immutable, mutable}
import com.twitter.json.Json
import com.twitter.xrayspecs.{Duration, Time}
import com.twitter.xrayspecs.TimeConversions._
import net.lag.logging.Logger


/**
 * Log all collected stats as a json line to a java logger at a regular interval.
 */
class JsonStatsLogger(val logger: Logger, val frequencyInSeconds: Int, includeJvmStats: Boolean)
      extends BackgroundProcess("JsonStatsLogger") {
  def this(logger: Logger, frequencyInSeconds: Int) = this(logger, frequencyInSeconds, true)

  val collection = Stats.fork()

  def nextRun: Duration = {
    // truncate to nearest round multiple of the desired repeat
    val t = Time.now + frequencyInSeconds.seconds
    ((t.inSeconds / frequencyInSeconds) * frequencyInSeconds).seconds - Time.now
  }

  def runLoop() {
    val delay = nextRun.inMilliseconds
    if (delay > 0) {
      Thread.sleep(delay)
    }

    logStats()
  }

  def logStats() {
    val statMap = collection.stats(true) ++
      immutable.Map("jvm" -> Stats.getJvmStats(), "gauges" -> Stats.getGaugeStats(true))
    logger.info(Json.build(immutable.Map(statMap.toSeq: _*)).toString)
  }
}
