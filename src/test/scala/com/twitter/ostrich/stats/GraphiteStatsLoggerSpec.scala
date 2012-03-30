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

import com.twitter.conversions.time._
import com.twitter.util.Time
import org.specs.SpecificationWithJUnit
import org.specs.SpecificationWithJUnit
import org.specs.mock.{ClassMocker, JMocker}
import java.net.Socket
import java.io.ByteArrayOutputStream

class GraphiteStatsLoggerSpec extends SpecificationWithJUnit with JMocker with ClassMocker {
  "GraphiteStatsLogger" should {
    var out = new ByteArrayOutputStream

    val socket = mock[Socket]

    var collection: StatsCollection = null
    var statsLogger: GraphiteStatsLogger = null

    doBefore {
      expect {
        atLeast(1).of(socket).getOutputStream willReturn out
        one(socket).close
     }

      collection = new StatsCollection()
      statsLogger = new GraphiteStatsLogger("localhost", 1123, 1.second, "server_pool", None, collection)
    }

    def getLines() = {
      out.toString.split("\n").toList
    }

    "log basic stats" in {
      collection.incr("cats")
      collection.incr("dogs", 3)
      statsLogger.write(socket)
      val lines = getLines().sorted
      lines(0) must beMatching("server_pool.unknown.cats 1.00 [0-9]+")
      lines(1) must beMatching("server_pool.unknown.dogs 3.00 [0-9]+")
    }

    "log timings" in {
      Time.withCurrentTimeFrozen { time =>
        collection.time("zzz") { time advance 10.milliseconds }
        collection.time("zzz") { time advance 20.milliseconds }
        statsLogger.write(socket)
        val lines = getLines().sorted
        lines(0) must beMatching("server_pool.unknown.zzz_msec_average 15.00 [0-9]+")
        lines(9) must beMatching("server_pool.unknown.zzz_msec_p99 19.00 [0-9]+")
      }
    }

    "log gauges" in {
      collection.setGauge("horse", 3.5)
      collection.setGauge("cow", 123456789.0)
      statsLogger.write(socket)
      val lines = getLines().sorted
      lines(0) must beMatching("server_pool.unknown.cow 123456789.00 [0-9]+")
      lines(1) must beMatching("server_pool.unknown.horse 3.50 [0-9]+")
    }
  }
}
