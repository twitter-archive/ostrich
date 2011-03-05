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
package stats

import scala.collection.immutable
import com.twitter.conversions.string._
import com.twitter.conversions.time._
import com.twitter.logging.{BareFormatter, Level, Logger, StringHandler}
import com.twitter.util.Time
import org.specs.Specification

object JsonStatsLoggerSpec extends Specification {
  "JsonStatsLogger" should {
    val logger = Logger.get("stats")
    logger.setLevel(Level.INFO)

    val handler = new StringHandler(BareFormatter, None)
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)

    var collection: StatsCollection = null
    var statsLogger: JsonStatsLogger = null

    def getLines() = {
      handler.get.split("\n").toList.filter { s => s.startsWith("#Fields") || !s.startsWith("#") }
    }

    doBefore {
      handler.clear()
      collection = new StatsCollection()
      statsLogger = new JsonStatsLogger(logger, 1.second, None, collection)
    }

    "log basic stats" in {
      collection.incr("cats")
      collection.incr("dogs", 3)
      statsLogger.periodic()
      val line = getLines()(0)
      line mustMatch "\"cats\":1"
      line mustMatch "\"dogs\":3"
    }

    "log timings" in {
      Time.withCurrentTimeFrozen { time =>
        collection.time("zzz") { time advance 10.milliseconds }
        collection.time("zzz") { time advance 20.milliseconds }
        statsLogger.periodic()
        val line = getLines()(0)
        line mustMatch "\"zzz_msec_count\":2"
        line mustMatch "\"zzz_msec_average\":15"
        line mustMatch "\"zzz_msec_p50\":10"
      }
    }

    "log gauges as ints when appropriate" in {
      collection.setGauge("horse", 3.5)
      collection.setGauge("cow", 1234567890.0)
      statsLogger.periodic()
      val line = getLines()(0)
      line mustMatch "\"horse\":3.5"
      line mustMatch "\"cow\":1234567890"
    }
  }
}
