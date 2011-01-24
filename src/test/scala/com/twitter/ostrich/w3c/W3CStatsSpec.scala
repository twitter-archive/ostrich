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
package w3c

import java.text.SimpleDateFormat
import java.util.Date
import scala.collection.immutable
import com.twitter.conversions.string._
import com.twitter.logging.{BareFormatter, Level, Logger, StringHandler}
import org.specs.Specification
import stats._

object W3CStatsSpec extends Specification {
  "w3c Stats" should {
    Logger.reset()

    val logger = Logger.get("w3c")
    logger.setLevel(Level.INFO)
    val handler = new StringHandler(BareFormatter, None)
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)

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

    doBefore {
      Logger.get("").setLevel(Level.OFF)
      Stats.clearAll()
      handler.clear()
    }

    def getLine() = {
      handler.get.split("\n").filter { line => !(line startsWith "#") }.head
    }

    "log and check some timings" in {
      w3c.transaction { stats =>
        val response: Int = stats.time[Int]("backend-response-time") {
          stats.setLabel("backend-response-method", "GET")
          stats.setLabel("request-uri", "/home")
          1 + 1
        }
        response mustEqual 2

        val response2: Int = stats.timeNanos[Int]("backend-response-time") {
          1 + 2
        }
        response2 mustEqual 3

        stats.setGauge("wodgets", 3.5)
      }

      val entries: Array[String] = getLine().split(" ")
      println(entries.toList)
      entries(0).toInt must be_>=(0)
      entries(1) mustEqual "GET"
      entries(2) mustEqual "/home"
      entries(3).toInt must be_>=(10)  //must take at least 10 ns!
      entries(4) mustEqual "-"
      entries(7) mustEqual "3.5"
    }

    "empty stats returns the empty string" in {
      w3c.transaction { stats => () }
      // strip out all unfound entries, and remove all whitespace. after that, it should be empty.
      getLine().replaceAll("-", "").trim() mustEqual ""
    }

    "logging a field not tracked in the fields member shouldn't show up in the logfile" in {
      w3c.transaction { stats =>
        stats.setLabel("jibberish_nonsense", "foo")
      }
      getLine() must notInclude("foo")
    }

    "sum multiple counts within a transaction" in {
      w3c.transaction { stats =>
        stats.incr("widgets", 8)
        stats.incr("widgets", 8)
      }
      getLine() mustEqual "- - - - - - 16 -"
    }
  }
}
