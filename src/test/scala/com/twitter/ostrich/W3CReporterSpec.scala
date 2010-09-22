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

import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import scala.collection.immutable
import com.twitter.xrayspecs.Time
import com.twitter.xrayspecs.TimeConversions._
import net.lag.extensions._
import net.lag.logging.{GenericFormatter, Level, Logger, StringHandler}
import org.specs._


class W3CReporterSpec extends Specification {
  "W3CReporter" should {
    val logger = Logger.get("w3c")
    logger.setLevel(Level.INFO)

    val handler = new StringHandler(new GenericFormatter("%2$s: "))
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)

    var reporter: W3CReporter = null

    def expectedHeader(crc: Long) = "w3c: #Version: 1.0" :: "w3c: #Date: 03-Aug-2009 19:23:04" :: ("w3c: #CRC: " + crc) :: Nil

    doBefore {
      Stats.clearAll()
      handler.clear()
      Time.now = Time.at("2009-08-03 19:23:04 +0000")
      reporter = new W3CReporter(logger, true, false)
    }

    "log basic stats" in {
      reporter.report(Map("cats" -> 10, "dogs" -> 9))
      handler.toString.split("\n").toList mustEqual
        expectedHeader(948200938) ::: "w3c: #Fields: cats dogs" :: "w3c: 10 9" :: Nil
    }

    "convert values appropriately" in {
      reporter.report(Map("date" -> new Date(0), "size" -> (1L << 32), "address" -> InetAddress.getByName("127.0.0.1"), "x" -> new Object))
      handler.toString.split("\n").last mustEqual "w3c: 127.0.0.1 01-Jan-1970_00:00:00 4294967296 -"
    }

    "not repeat the header too often" in {
      reporter.report(Map("a" -> 1))
      reporter.report(Map("a" -> 2))
      reporter.nextHeaderDumpAt = Time.now
      reporter.report(Map("a" -> 3))
      handler.toString.split("\n").toList mustEqual
        expectedHeader(276919822) :::
        "w3c: #Fields: a" ::
        "w3c: 1" ::
        "w3c: 2" ::
        expectedHeader(276919822) :::
        "w3c: #Fields: a" ::
        "w3c: 3" :: Nil
    }

    "repeat the header when the fields change" in {
      reporter.report(Map("a" -> 1))
      reporter.report(Map("a" -> 2))
      reporter.report(Map("a" -> 3, "b" -> 1))
      handler.toString.split("\n").toList mustEqual
        expectedHeader(276919822) :::
        "w3c: #Fields: a" ::
        "w3c: 1" ::
        "w3c: 2" ::
        expectedHeader(1342496559) :::
        "w3c: #Fields: a b" ::
        "w3c: 3 1" :: Nil
    }

    "per line crc printing"  >> {
      val crcReporter = new W3CReporter(logger, true, true)

      "should print" in {
        crcReporter.report(Map("a" -> 3, "b" -> 1))
        handler.toString.split("\n").toList mustEqual
          expectedHeader(1342496559) :::
          "w3c: #Fields: a b" ::
          "w3c: 1342496559 3 1" :: Nil
      }

      "changes appropriately when column headers change" in {
        crcReporter.report(Map("a" -> 1))
        crcReporter.report(Map("a" -> 3, "b" -> 1))
        handler.toString.split("\n").toList mustEqual
          expectedHeader(276919822) :::
          "w3c: #Fields: a" ::
          "w3c: 276919822 1" ::
          expectedHeader(1342496559) :::
          "w3c: #Fields: a b" ::
          "w3c: 1342496559 3 1" :: Nil
      }
    }
  }
}
