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

import java.text.SimpleDateFormat
import java.util.Date
import scala.collection.immutable
import com.twitter.conversions.string._
import com.twitter.logging.{Formatter, Level, Logger, StringHandler}
import org.specs.Specification

object W3CStatsSpec extends Specification {
  "w3c Stats" should {
    val logger = Logger.get("w3c")
    logger.setLevel(Level.INFO)
    val formatter = new Formatter {
      override def lineTerminator = ""
      override def dateFormat = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS")
      override def formatPrefix(level: java.util.logging.Level, date: String, name: String) = name + ": "
    }
    val handler = new StringHandler(formatter, None)
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)

    val w3c = new W3CStats(logger, Array("backend-response-time", "backend-response-method", "request-uri", "backend-response-time_ns", "unsupplied-field", "finish_timestamp", "widgets", "wodgets"))

    doBefore {
      Logger.get("").setLevel(Level.OFF)
      Stats.clearAll()
      handler.clear()
    }

    "log and check some timings" in {
      val response: Int = w3c.time[Int]("backend-response-time") {
        w3c.log("backend-response-method", "GET")
        w3c.log("request-uri", "/home")
        1 + 1
      }
      response mustEqual 2

      w3c.log("finish_timestamp", new Date(0))

      val response2: Int = w3c.timeNanos[Int]("backend-response-time_ns") {
        1 + 2
      }
      response2 mustEqual 3

      val logline = w3c.log_entry
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
      w3c.log("request-uri", "foo")
      w3c.clearAll()
      val logline = w3c.log_entry
      // strip out all unfound entries, and remove all whitespace. after that, it should be empty.
      logline.replaceAll("-", "").trim() mustEqual ""
    }

    "logging a field not tracked in the fields member shouldn't show up in the logfile" in {
      w3c.log("jibberish_nonsense", "foo")
      w3c.log_entry must notInclude("foo")
    }

    "handle a transaction" in {
      w3c.log("request-uri", "foo")
      w3c.transaction {
        w3c.log("widgets", 8)
        w3c.log("wodgets", 3)
      }
      handler.get.replaceAll(" -", "") mustEqual "w3c: 8 3"
    }

    "sum multiple counts within a transaction" in {
      w3c.transaction {
        w3c.log("widgets", 8)
        w3c.log("widgets", 8)
      }
      handler.get.replaceAll(" -", "") mustEqual "w3c: 16"
    }

    "concat multiple string values within a transaction" in {
      w3c.transaction {
        w3c.log("widgets", "hello")
        w3c.log("widgets", "kitty")
      }
      handler.get.replaceAll(" -", "") mustEqual "w3c: hello,kitty"
    }
  }
}
