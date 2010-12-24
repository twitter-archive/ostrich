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

import scala.collection.immutable
import com.twitter.util.Time
import com.twitter.util.TimeConversions._
import net.lag.extensions._
import net.lag.logging.{GenericFormatter, Level, Logger, StringHandler}
import org.specs._


object W3CStatsLoggerSpec extends Specification {
  "W3CStatsLogger" should {
    val logger = Logger.get("w3c")
    logger.setLevel(Level.INFO)

    val handler = new StringHandler(new GenericFormatter(""))
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)

    var statsLogger: W3CStatsLogger = null

    def getLines() = {
      handler.toString.split("\n").toList.filter { s => s.startsWith("#Fields") || !s.startsWith("#") }
    }

    doBefore {
      Stats.clearAll()
      handler.clear()
      statsLogger = new W3CStatsLogger(logger, 1, false)
    }

    "log basic stats" in {
      Stats.incr("cats")
      Stats.incr("dogs", 3)
      statsLogger.logStats()
      getLines() mustEqual "#Fields: cats dogs" :: "1 3" :: Nil
    }

    "log timings" in {
      Time.withCurrentTimeFrozen { tc =>
        Stats.time("zzz") { tc.advance(10.milliseconds) }
        Stats.time("zzz") { tc.advance(20.milliseconds) }
        statsLogger.logStats()
        getLines() mustEqual "#Fields: zzz_avg zzz_count zzz_max zzz_min zzz_std" :: "15 2 20 10 7" :: Nil
      }
    }

    "log multiple lines" in {
      Time.withCurrentTimeFrozen { tc =>
        Stats.incr("cats")
        Stats.incr("dogs", 3)
        Stats.time("zzz") { tc.advance(10.milliseconds) }
        statsLogger.logStats()
        Stats.incr("cats")
        Stats.time("zzz") { tc.advance(20.milliseconds) }
        statsLogger.logStats()
        getLines() mustEqual "#Fields: cats dogs zzz_avg zzz_count zzz_max zzz_min zzz_std" ::
          "1 3 10 1 10 10 0" :: "1 0 20 1 20 20 0" :: Nil
      }
    }
  }
}
