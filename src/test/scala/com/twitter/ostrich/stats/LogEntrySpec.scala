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

import java.text.SimpleDateFormat
import java.util.Date
import scala.collection.immutable
import com.twitter.ostrich.w3c.W3CLogFormat
import com.twitter.logging.{BareFormatter, Level, Logger, StringHandler}
import org.specs._

object LogEntrySpec extends Specification {
  "log entries" should {
    val logger = Logger.get("testlog")
    logger.setLevel(Level.INFO)
    val handler = new StringHandler(BareFormatter, None)
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)

    var entry: LogEntry = null
    val fields = Array("backend-response-time_msec", "backend-response-method", "request-uri",
      "backend-response-time_ns", "unsupplied-field", "finish_timestamp", "widgets", "wodgets")

    // TODO(benjy): For historical reasons we test against the W3C format, but we should probably use a
    // test-specific format, for isolation.
    doBefore {
      handler.clear()
      entry = new LogEntry(logger, new W3CLogFormat(false), fields)
    }

    "starts life with an empty map" in {
      entry.map.size mustEqual 0
    }

    "log and check a single timing" in {
      entry.addMetric("backend-response-time_msec", 57)
      entry.flush
      handler.get must beMatching("57")
      handler.clear()
    }

    "flushing ensures that the entry is gone" in {
      entry.addMetric("backend-response-time_msec", 57)
      entry.flush
      handler.clear()

      entry.flush
      handler.get mustNot beMatching("57")
    }

    "incr works with positive and negative numbers" in {
      entry.incr("wodgets", 1)
      entry.incr("wodgets")

      entry.incr("widgets", 1)
      entry.incr("widgets", -1)
      entry.flush

      handler.get must endWith("0 2\n")
    }

    "works with Strings" in {
      entry.log("backend-response-time_msec", "57")
      entry.flush
      handler.get must beMatching("57")
    }

    "rejects a column that isn't registered" in {
      entry.incr("whatwhatlol", 100)
      handler.get mustNot beMatching("100")
    }

    "start and end Timing" in {
      "flushing before ending a timing means it doesn't get logged" in {
        entry.startTiming("backend-response-time")
        entry.flush
        handler.get must startWith("- ")
      }

      "end" in {
        entry.startTiming("backend-response-time")
        entry.endTiming("backend-response-time")
        entry.flush
        handler.get mustNot startWith("- ")
      }
    }
  }
}
