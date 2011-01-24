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
package json

import scala.collection.immutable
import com.twitter.conversions.string._
import com.twitter.conversions.time._
import com.twitter.logging.{BareFormatter, Level, Logger, StringHandler}
import com.twitter.util.Time
import org.specs._
import stats._

object JsonStatsLoggerSpec extends Specification {
  "JsonStatsLogger" should {
    val logger = Logger.get("stats")
    logger.setLevel(Level.INFO)

    val handler = new StringHandler(BareFormatter, None)
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)

    var statsLogger: JsonStatsLogger = null

    def getLines() = {
      handler.get.split("\n").toList.filter { s => s.startsWith("#Fields") || !s.startsWith("#") }
    }

    doBefore {
      Stats.clearAll()
      handler.clear()
      statsLogger = new JsonStatsLogger(logger, 1.second)
    }

    "log basic stats" in {
      Stats.incr("cats")
      Stats.incr("dogs", 3)
      statsLogger.periodic()
      val line = getLines()(0)
      line mustMatch "\"cats\":1"
      line mustMatch "\"dogs\":3"
    }

    "log timings" in {
      Time.withCurrentTimeFrozen { time =>
        Stats.time("zzz") { time advance 10.milliseconds }
        Stats.time("zzz") { time advance 20.milliseconds }
        statsLogger.periodic()
        val line = getLines()(0)
        line mustMatch "\"zzz_msec_count\":2"
        line mustMatch "\"zzz_msec_average\":15"
        line mustMatch "\"zzz_msec_p50\":10"
      }
    }
  }
}
