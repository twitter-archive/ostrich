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

package com.twitter.ostrich.stats

import com.twitter.conversions.string._
import com.twitter.logging.{BareFormatter, Level, Logger, StringHandler}
import java.text.SimpleDateFormat
import java.util.Date
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite

@RunWith(classOf[JUnitRunner])
class W3CStatsTest extends FunSuite {

  class Context {
    val logger = Logger.get("w3c")
    var handler: StringHandler = null

    val fields = Array(
      "backend-response-time_msec_average",
      "backend-response-method",
      "request-uri",
      "backend-response-time_nsec_average",
      "unsupplied-field",
      "finish_timestamp",
      "widgets",
      "wodgets"
    )

    val w3c = new W3CStats(logger, fields, false)

    handler = new StringHandler(BareFormatter, None)
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)
    logger.setLevel(Level.INFO)

    Logger.get("").setLevel(Level.OFF)
    Stats.clearAll()
    handler.clear()

    def getLine() = {
      val rv = handler.get.split("\n").filter { line => !(line startsWith "#") }.head
      handler.clear()
      rv
    }
  }

  test("can be called manually") {
    val context = new Context
    import context._

    val counters = Map("widgets" -> 3L)
    val gauges = Map("wodgets" -> 3.5)
    val metrics = Map("backend-response-time_msec" -> new Distribution(Histogram(10)))
    val labels = Map("request-uri" -> "/home")
    w3c.write(StatsSummary(counters, metrics, gauges, labels))
    assert(getLine() === "10 - /home - - - 3 3.5")
  }

  test("can be called transactionally") {
    val context = new Context
    import context._

    w3c { stats =>
      val response: Int = stats.time[Int]("backend-response-time") {
        stats.setLabel("backend-response-method", "GET")
        stats.setLabel("request-uri", "/home")
        1 + 1
      }
      assert(response === 2)

      val response2: Int = stats.timeNanos[Int]("backend-response-time") {
        1 + 2
      }
      assert(response2 === 3)

      stats.setGauge("wodgets", 3.5)
    }

    val entries: Array[String] = getLine().split(" ")
    assert(entries(0).toInt >= 0)
    assert(entries(1) === "GET")
    assert(entries(2) === "/home")
    assert(entries(3).toInt >= 10)  //must take at least 10 ns!
    assert(entries(4) === "-")
    assert(entries(7) === "3.5")
  }

  test("empty stats returns the empty string") {
    val context = new Context
    import context._

    w3c { stats => () }
    // strip out all unfound entries, and remove all whitespace. after that, it should be empty.
    assert(getLine().replaceAll("-", "").trim() === "")
  }

  test("logging a field not tracked in the fields member shouldn't show up in the logfile") {
    val context = new Context
    import context._

    w3c { stats =>
      stats.setLabel("jibberish_nonsense", "foo")
    }
    assert(!getLine().contains("foo"))
  }

  test("sum counts within a transaction") {
    val context = new Context
    import context._

    w3c { stats =>
      stats.incr("widgets", 8)
      stats.incr("widgets", 8)
    }
    assert(getLine() === "- - - - - - 16 -")
  }

  test("logs metrics only once") {
    val context = new Context
    import context._

    w3c { stats =>
      stats.addMetric("backend-response-time_msec", 9)
      stats.addMetric("backend-response-time_msec", 13)
    }
    assert(getLine() === "11 - - - - - - -")
    w3c { stats =>
      stats.addMetric("backend-response-time_msec", 9)
    }
    assert(getLine() === "9 - - - - - - -")
  }

}
