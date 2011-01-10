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

import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import scala.collection.immutable
import com.twitter.conversions.string._
import com.twitter.conversions.time._
import com.twitter.logging.{BareFormatter, Level, Logger, StringHandler}
import com.twitter.util.Time
import org.specs.Specification
import stats._

class W3CReporterSpec extends Specification {
  "W3CReporter" should {
    val logger = Logger.get("w3c")
    logger.setLevel(Level.INFO)

    val handler = new StringHandler(BareFormatter, None)
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)

    var reporter: W3CReporter = null

    def expectedHeader(crc: Long) = "#Version: 1.0" :: "#Date: 03-Aug-2009 19:23:04" :: ("#CRC: " + crc) :: Nil

    val now = Time.at("2009-08-03 19:23:04 +0000")

    doBefore {
      Stats.clearAll()
      handler.clear()
      reporter = new W3CReporter(logger)
    }

    "log basic stats" in {
      Time.withTimeAt(now) { time =>
        reporter.report(Map("cats" -> 10, "dogs" -> 9))
        handler.get.split("\n").toList mustEqual
          expectedHeader(948200938) ::: "#Fields: cats dogs" :: "10 9" :: Nil
      }
    }

    "convert values appropriately" in {
      Time.withTimeAt(now) { time =>
        reporter.report(Map("date" -> new Date(0), "size" -> (1L << 32), "address" -> InetAddress.getByName("127.0.0.1"), "x" -> new Object))
        handler.get.split("\n").last mustEqual "127.0.0.1 01-Jan-1970_00:00:00 4294967296 -"
      }
    }

    "not repeat the header too often" in {
      Time.withTimeAt(now) { time =>
        reporter.report(Map("a" -> 1))
        reporter.report(Map("a" -> 2))
        reporter.nextHeaderDumpAt = Time.now
        reporter.report(Map("a" -> 3))
        handler.get.split("\n").toList mustEqual
          expectedHeader(276919822) :::
          "#Fields: a" ::
          "1" ::
          "2" ::
          expectedHeader(276919822) :::
          "#Fields: a" ::
          "3" :: Nil
      }
    }

    "repeat the header when the fields change" in {
      Time.withTimeAt(now) { time =>
        reporter.report(Map("a" -> 1))
        reporter.report(Map("a" -> 2))
        reporter.report(Map("a" -> 3, "b" -> 1))
        handler.get.split("\n").toList mustEqual
          expectedHeader(276919822) :::
          "#Fields: a" ::
          "1" ::
          "2" ::
          expectedHeader(1342496559) :::
          "#Fields: a b" ::
          "3 1" :: Nil
      }
    }

    "per line crc printing"  >> {
      val crcReporter = new W3CReporter(logger, true)

      "should print" in {
        Time.withTimeAt(now) { time =>
          crcReporter.report(Map("a" -> 3, "b" -> 1))
          handler.get.split("\n").toList mustEqual
            expectedHeader(1342496559) :::
            "#Fields: a b" ::
            "1342496559 3 1" :: Nil
        }
      }

      "changes appropriately when column headers change" in {
        Time.withTimeAt(now) { time =>
          crcReporter.report(Map("a" -> 1))
          crcReporter.report(Map("a" -> 3, "b" -> 1))
          handler.get.split("\n").toList mustEqual
            expectedHeader(276919822) :::
            "#Fields: a" ::
            "276919822 1" ::
            expectedHeader(1342496559) :::
            "#Fields: a b" ::
            "1342496559 3 1" :: Nil
        }
      }
    }
  }
}
