/*
 * Copyright 2011 Twitter, Inc.
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

package com.twitter.ostrich.json

import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import scala.collection.immutable.HashMap
import com.twitter.json.Json
import com.twitter.xrayspecs.Time
import com.twitter.xrayspecs.TimeConversions._
import org.specs._


class JsonLogFormatSpec extends Specification {
  "JsonLogFormat" should {
    val logFormat: JsonLogFormat = new JsonLogFormat()

    "format correctly" in {

      val vals1 = HashMap("dogs" -> 9, "cats" -> 10)
      val vals2 = HashMap("foo" -> "foo", "bar" -> 127)
      Json.parse(logFormat.generateLine(vals1.keys.toList, vals1)) mustEqual vals1
      Json.parse(logFormat.generateLine(vals2.keys.toList, vals2)) mustEqual vals2
    }

    "convert values appropriately" in {
      val vals3 = HashMap("ip" -> InetAddress.getByName("127.0.0.1"), "date" -> new Date(0))
      val vals3_res = HashMap("ip" -> "127.0.0.1", "date" -> "01-Jan-1970 00:00:00")
      Json.parse(logFormat.generateLine(vals3.keys.toList, vals3)) mustEqual vals3_res
    }
  }
}

