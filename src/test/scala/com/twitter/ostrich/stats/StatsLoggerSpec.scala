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
import com.twitter.conversions.time._
import com.twitter.logging.{BareFormatter, Level, Logger, StringHandler}
import com.twitter.ostrich.w3c.W3CLogFormat
import com.twitter.util.Time
import org.specs._

object StatsLoggerSpec extends Specification {
  "StatsLogger" should {
    val logger = Logger.get("testlog")
    logger.setLevel(Level.INFO)

    val handler = new StringHandler(BareFormatter, None)
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)

    var collection: StatsCollection = null
    var statsLogger: StatsLogger = null

    def getLines() = {
      handler.get.split("\n").toList.filter { s => s.startsWith("#Fields") || !s.startsWith("#") }
    }

    doBefore {
      handler.clear()
      collection = new StatsCollection()
      // TODO(benjy): For historical reasons we test against the W3C format, but we should probably use a
      // test-specific format, for isolation.
      statsLogger = new StatsLogger(new LogReporter(logger, new W3CLogFormat(false)), 1.second, collection)
    }

    "log basic stats" in {
      collection.incr("cats")
      collection.incr("dogs", 3)
      statsLogger.periodic()
      getLines() mustEqual "#Fields: cats dogs" :: "1 3" :: Nil
    }

    "log timings" in {
      Time.withCurrentTimeFrozen { time =>
        collection.time("zzz") { time advance 10.milliseconds }
        collection.time("zzz") { time advance 20.milliseconds }
        statsLogger.periodic()
        getLines() mustEqual "#Fields: zzz_msec_avg zzz_msec_count zzz_msec_max zzz_msec_min" :: "15 2 20 10" :: Nil
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
        getLines() mustEqual "#Fields: cats dogs zzz_msec_avg zzz_msec_count zzz_msec_max zzz_msec_min" ::
          "1 3 10 1 10 10" :: "1 0 20 1 20 20" :: Nil
      }
    }
  }
}
