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

package com.twitter.ostrich.w3c

import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import scala.collection.immutable
import com.twitter.conversions.time._
import com.twitter.util.Time
import org.specs._

class W3CLogFormatSpec extends Specification {
  "W3CLogFormat" should {
    val logFormat: W3CLogFormat = new W3CLogFormat(false)

    "format correctly" in {
      val vals = Map("cats" -> 10, "dogs" -> 9)
      logFormat.generateLine(vals.keys.toList, vals) mustEqual "10 9"
    }

    "generate headers correctly" in {
      Time.withTimeAt(Time.at("2009-08-03 19:23:04 UTC")) { time =>
        val vals = List("cats", "dogs")
        logFormat.generateHeader(vals).getOrElse("") mustEqual
          "#Version: 1.0\n#Date: 03-Aug-2009 19:23:04\n#CRC: 948200938\n#Fields: cats dogs"
      }
    }

    "convert values appropriately" in {
      val vals1 = Map("date" -> new Date(0), "size" -> (1L << 32),
                      "address" -> InetAddress.getByName("127.0.0.1"), "x" -> new Object)
      logFormat.generateLine(vals1.keys.toList.sorted, vals1) mustEqual "127.0.0.1 01-Jan-1970_00:00:00 4294967296 -"
    }

    "detect when the header changes" in {
      logFormat.generateHeader(List("a"))
      logFormat.generateHeader(List("a"))
        logFormat.headerChanged mustEqual false

      logFormat.generateHeader(List("a", "b"))
      logFormat.headerChanged mustEqual true
    }

    "per line crc printing" in {
      val crcLogFormat = new W3CLogFormat(true)

      "should print" in {
        val fields = List("a", "b")
        crcLogFormat.generateHeader(fields)  // To cause crc to be generated for the header.
        crcLogFormat.generateLine(fields, Map("a" -> 3, "b" -> 1))  mustEqual "1342496559 3 1"
      }

      "changes appropriately when column headers change" in {
        val fields1 = List("a")
        crcLogFormat.generateHeader(fields1)
        crcLogFormat.generateLine(fields1, Map("a" -> 1))  mustEqual "276919822 1"

        val fields2 = List("a", "b")
        crcLogFormat.generateHeader(fields2)
        crcLogFormat.generateLine(fields2, Map("a" -> 3, "b" -> 1))  mustEqual "1342496559 3 1"
      }
    }
  }
}
