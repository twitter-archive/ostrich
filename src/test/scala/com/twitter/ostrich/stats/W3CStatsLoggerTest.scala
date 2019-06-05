/*
 * Copyright 2009 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.ostrich.stats

import com.twitter.conversions.string._
import com.twitter.conversions.time._
import com.twitter.logging.{BareFormatter, Level, Logger, StringHandler}
import com.twitter.util.Time
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite

@RunWith(classOf[JUnitRunner])
class W3CStatsLoggerTest extends FunSuite {

  class Context {
    val logger = Logger.get("w3c")

    var handler: StringHandler = null
    var collection: StatsCollection = null
    var statsLogger: W3CStatsLogger = null

    def getLines() = {
      val rv = handler.get.split("\n").toList.filter { s => s.startsWith("#Fields") || !s.startsWith("#") }
      handler.clear()
      rv
    }

    handler = new StringHandler(BareFormatter, None)
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)
    logger.setLevel(Level.INFO)

    collection = new StatsCollection()
    handler.clear()
    statsLogger = new W3CStatsLogger(logger, 1.second, collection)
  }

  test("log basic stats") {
    val context = new Context
    import context._

    collection.incr("cats")
    collection.incr("dogs", 3)
    statsLogger.periodic()
    assert(getLines() == "#Fields: cats dogs" :: "948200938 1 3" :: Nil)
  }

  test("log timings") {
    val context = new Context
    import context._

    Time.withCurrentTimeFrozen { time =>
      collection.time("zzz") { time advance 10.milliseconds }
      collection.time("zzz") { time advance 20.milliseconds }
      statsLogger.periodic()
      assert(getLines() == List(
        "#Fields: zzz_msec_average zzz_msec_count zzz_msec_maximum zzz_msec_minimum zzz_msec_sum",
        "1176525931 15 2 19 10 30"
      ))
    }
  }

  test("log multiple lines") {
    val context = new Context
    import context._

    Time.withCurrentTimeFrozen { time =>
      collection.incr("cats")
      collection.incr("dogs", 3)
      collection.time("zzz") { time advance 10.milliseconds }
      statsLogger.periodic()
      collection.incr("cats")
      collection.time("zzz") { time advance 20.milliseconds }
      statsLogger.periodic()
      assert(getLines() == List(
        "#Fields: cats dogs zzz_msec_average zzz_msec_count zzz_msec_maximum zzz_msec_minimum zzz_msec_sum",
        "2826312472 1 3 10 1 10 10 10",
        "2826312472 1 0 20 1 19 19 20"
      ))
    }
  }

  test("not repeat the header too often") {
    val context = new Context
    import context._

    Time.withCurrentTimeFrozen { time =>
      collection.incr("cats")
      statsLogger.periodic()
      assert(getLines() == "#Fields: cats" :: "2001103910 1" :: Nil)
      collection.incr("cats")
      statsLogger.periodic()
      assert(getLines() == "2001103910 1" :: Nil)
      time advance 10.minutes
      collection.incr("cats")
      statsLogger.periodic()
      assert(getLines() == "#Fields: cats" :: "2001103910 1" :: Nil)
    }
  }

  test("repeat the header when the fields change") {
    val context = new Context
    import context._

    collection.incr("cats")
    statsLogger.periodic()
    assert(getLines() == "#Fields: cats" :: "2001103910 1" :: Nil)
    collection.incr("cats")
    statsLogger.periodic()
    assert(getLines() == "2001103910 1" :: Nil)
    collection.incr("cats")
    collection.incr("dogs")
    statsLogger.periodic()
    assert(getLines() == "#Fields: cats dogs" :: "948200938 1 1" :: Nil)
  }

}
