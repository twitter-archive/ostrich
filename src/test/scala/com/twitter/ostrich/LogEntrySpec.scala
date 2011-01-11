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


object LogEntrySpec extends Specification {
  "log entries" should {
    val logger = Logger.get("testlog")
    logger.setLevel(Level.INFO)
    val formatter = new Formatter {
      override def lineTerminator = ""
      override def dateFormat = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS")
      override def formatPrefix(level: java.util.logging.Level, date: String, name: String) = ""
    }

    val handler = new StringHandler(formatter)
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)

    // TODO(benjy): For historical reasons we test against the W3C format, but we should probably use a
    // test-specific format, for isolation.
    val entry = new LogEntry(logger, new W3CLogFormat(false),
                             Array("backend-response-time", "backend-response-method", "request-uri",
                                   "backend-response-time_ns", "unsupplied-field", "finish_timestamp", "widgets", "wodgets"))
    doBefore {
      Stats.clearAll()
      handler.clear()
    }

    "starts life with an empty map" in {
      entry.map.size mustEqual 0
    }

    "log and check a single timing" in {
      entry.addTiming("backend-response-time", 57)
      entry.flush
      handler.toString() must beMatching("57")
      handler.clear()
    }

    "flushing ensures that the entry is gone" in {
      entry.addTiming("backend-response-time", 57)
      entry.flush
      handler.clear()

      entry.flush
      handler.toString() mustNot beMatching("57")
    }

    "incr works with positive and negative numbers" in {
      entry.incr("wodgets", 1)
      entry.incr("wodgets")

      entry.incr("widgets", 1)
      entry.incr("widgets", -1)
      entry.flush

      handler.toString() must endWith("0 2")
    }

    "works with Strings" in {
      entry.log("backend-response-time", "57")
      entry.flush
      handler.toString must beMatching("57")
    }

    "rejects a column that isn't registered" in {
      entry.incr("whatwhatlol", 100)
      handler.toString() mustNot beMatching("100")
    }

    "start and end Timing" in {
      "flushing before ending a timing means it doesn't get logged" in {
        entry.startTiming("backend-response-time")
        entry.flush
        handler.toString() must startWith("- ")
      }

      "end" in {
        entry.startTiming("backend-response-time")
        entry.endTiming("backend-response-time")
        entry.flush
        handler.toString() mustNot startWith("- ")
      }
    }
  }
}
