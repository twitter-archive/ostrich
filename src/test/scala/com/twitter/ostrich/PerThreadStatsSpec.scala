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

import com.twitter.ostrich.w3c.W3CLogFormat
import net.lag.extensions._
import net.lag.logging.{Formatter, Level, Logger, StringHandler}
import org.specs._
import scala.collection.immutable
import java.text.SimpleDateFormat
import java.util.Date


object PerThreadStatsSpec extends Specification {
  "PerTheadStats" should {
    val logger = Logger.get("w3c")
    logger.setLevel(Level.INFO)
    val formatter = new Formatter {
      override def lineTerminator = ""
      override def dateFormat = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS")
      override def formatPrefix(level: java.util.logging.Level, date: String, name: String) = name + ": "
    }
    val handler = new StringHandler(formatter)
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)

    // TODO(benjy): For historical reasons we test against the W3C format, but we should probably use a
    // test-specific format, for isolation.
    val perThreadStats = new PerThreadStats(logger, new W3CLogFormat(false),
                                            Array("backend-response-time", "backend-response-method",
                                                  "request-uri", "backend-response-time_ns",
                                                  "unsupplied-field", "finish_timestamp", "widgets", "wodgets"))

    doBefore {
      Stats.clearAll()
      handler.clear()
    }

    "log and check some timings" in {
      val response: Int = perThreadStats.time[Int]("backend-response-time") {
        perThreadStats.log("backend-response-method", "GET")
        perThreadStats.log("request-uri", "/home")
        1 + 1
      }
      response mustEqual 2

      perThreadStats.log("finish_timestamp", new Date(0))

      val response2: Int = perThreadStats.timeNanos[Int]("backend-response-time_ns") {
        1 + 2
      }
      response2 mustEqual 3

      val logline = perThreadStats.log_entry
      logline mustNot beNull

      val entries: Array[String] = logline.split(" ")
      entries(0).toInt must be_>=(0)
      entries(1) mustEqual "GET"
      entries(2) mustEqual "/home"
      entries(3).toInt must be_>=(10)  //must take at least 10 ns!
      entries(4) mustEqual "-"
      entries(5) mustEqual "01-Jan-1970_00:00:00"
    }

    "map when cleared returns the empty string" in {
      perThreadStats.log("request-uri", "foo")
      perThreadStats.clearAll()
      val logline = perThreadStats.log_entry
      // strip out all unfound entries, and remove all whitespace. after that, it should be empty.
      logline.replaceAll("-", "").trim() mustEqual ""
    }

    "logging a field not tracked in the fields member shouldn't show up in the logfile" in {
      perThreadStats.log("jibberish_nonsense", "foo")
      perThreadStats.log_entry must notInclude("foo")
    }

    "handle a transaction" in {
      perThreadStats.log("request-uri", "foo")
      perThreadStats.transaction {
        perThreadStats.log("widgets", 8)
        perThreadStats.log("wodgets", 3)
      }
      handler.toString.replaceAll(" -", "") mustEqual "w3c: 8 3"
    }

    "sum multiple counts within a transaction" in {
      perThreadStats.transaction {
        perThreadStats.log("widgets", 8)
        perThreadStats.log("widgets", 8)
      }
      handler.toString.replaceAll(" -", "") mustEqual "w3c: 16"
    }

    "concat multiple string values within a transaction" in {
      perThreadStats.transaction {
        perThreadStats.log("widgets", "hello")
        perThreadStats.log("widgets", "kitty")
      }
      handler.toString.replaceAll(" -", "") mustEqual "w3c: hello,kitty"
    }
  }
}
