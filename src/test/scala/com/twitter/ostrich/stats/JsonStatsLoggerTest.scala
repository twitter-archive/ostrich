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
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite

@RunWith(classOf[JUnitRunner])
class JsonStatsLoggerTest extends FunSuite {

  class Context {
    val logger = Logger.get("stats")

    var handler: StringHandler = null
    var collection: StatsCollection = null
    var statsLogger: JsonStatsLogger = null

    def getLines() = {
      handler.get.split("\n").toList.filter { s => s.startsWith("#Fields") || !s.startsWith("#") }
    }

    handler = new StringHandler(BareFormatter, None)
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)
    logger.setLevel(Level.INFO)
    handler.clear()
    collection = new StatsCollection()
    statsLogger = new JsonStatsLogger(logger, 1.second, None, collection)
  }

  test("log basic stats") {
    val context = new Context
    import context._

    collection.incr("cats")
    collection.incr("dogs", 3)
    statsLogger.periodic()
    val line = getLines()(0)
    assert(line.contains("\"cats\":1"))
    assert(line.contains("\"dogs\":3"))
  }

  test("log timings") {
    val context = new Context
    import context._

    Time.withCurrentTimeFrozen { time =>
      collection.time("zzz") { time advance 10.milliseconds }
      collection.time("zzz") { time advance 20.milliseconds }
      statsLogger.periodic()
      val line = getLines()(0)
      assert(line.contains("\"zzz_msec_count\":2"))
      assert(line.contains("\"zzz_msec_average\":15"))
      assert(line.contains("\"zzz_msec_p50\":10"))
    }
  }

  test("log gauges as ints when appropriate") {
    val context = new Context
    import context._

    collection.setGauge("horse", 3.5)
    collection.setGauge("cow", 1234567890.0)
    statsLogger.periodic()
    val line = getLines()(0)
    assert(line.contains("\"horse\":3.5"))
    assert(line.contains("\"cow\":1234567890"))
  }

}
