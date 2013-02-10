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

import scala.collection.immutable
import com.twitter.conversions.string._
import com.twitter.conversions.time._
import com.twitter.logging.{BareFormatter, Level, Logger, StringHandler}
import com.twitter.util.Time
import org.specs.SpecificationWithJUnit

class W3CStatsLoggerSpec extends SpecificationWithJUnit {
  "W3CStatsLogger" should {
    val logger = Logger.get("w3c")
    logger.setLevel(Level.INFO)

    val handler = new StringHandler(BareFormatter, None)
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)

    var collection: StatsCollection = null
    var statsLogger: W3CStatsLogger = null

    def getLines() = {
      val rv = handler.get.split("\n").toList.filter { s => s.startsWith("#Fields") || !s.startsWith("#") }
      handler.clear()
      rv
    }

    doBefore {
      collection = new StatsCollection()
      handler.clear()
      statsLogger = new W3CStatsLogger(logger, 1.second, collection)
    }

    "log basic stats" in {
      collection.incr("cats")
      collection.incr("dogs", 3)
      statsLogger.periodic()
      getLines() mustEqual "#Fields: cats dogs" :: "948200938 1 3" :: Nil
    }

    "log timings" in {
      Time.withCurrentTimeFrozen { time =>
        collection.time("zzz") { time advance 10.milliseconds }
        collection.time("zzz") { time advance 20.milliseconds }
        statsLogger.periodic()
        getLines() mustEqual List(
          "#Fields: zzz_msec_average zzz_msec_count zzz_msec_maximum zzz_msec_minimum zzz_msec_stdDev zzz_msec_sum",
          "447159087 15 2 19 10 5 30"
        )
      }
    }

    "log multiple lines" in {
      Time.withCurrentTimeFrozen { time =>
        collection.incr("cats")
        collection.incr("dogs", 3)
        collection.time("zzz") { time advance 10.milliseconds }
        statsLogger.periodic()
        collection.incr("cats")
        collection.time("zzz") { time advance 20.milliseconds }
        statsLogger.periodic()
        getLines() mustEqual List(
          "#Fields: cats dogs zzz_msec_average zzz_msec_count zzz_msec_maximum zzz_msec_minimum zzz_msec_stdDev zzz_msec_sum",
          "910498673 1 3 10 1 10 10 0 10",
          "910498673 1 0 20 1 19 19 0 20"
        )
      }
    }

    "not repeat the header too often" in {
      Time.withCurrentTimeFrozen { time =>
        collection.incr("cats")
        statsLogger.periodic()
        getLines() mustEqual "#Fields: cats" :: "2001103910 1" :: Nil
        collection.incr("cats")
        statsLogger.periodic()
        getLines() mustEqual "2001103910 1" :: Nil
        time advance 10.minutes
        collection.incr("cats")
        statsLogger.periodic()
        getLines() mustEqual "#Fields: cats" :: "2001103910 1" :: Nil
      }
    }

    "repeat the header when the fields change" in {
      collection.incr("cats")
      statsLogger.periodic()
      getLines() mustEqual "#Fields: cats" :: "2001103910 1" :: Nil
      collection.incr("cats")
      statsLogger.periodic()
      getLines() mustEqual "2001103910 1" :: Nil
      collection.incr("cats")
      collection.incr("dogs")
      statsLogger.periodic()
      getLines() mustEqual "#Fields: cats dogs" :: "948200938 1 1" :: Nil
    }
  }
}
