/*
 * Copyright 2009 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.ostrich.stats

import com.twitter.conversions.time._
import com.twitter.util.Time
import java.net.Socket
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern
import org.junit.runner.RunWith
import org.mockito.Mockito.{verify, times, when}
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar

@RunWith(classOf[JUnitRunner])
class GraphiteStatsLoggerTest extends FunSuite with MockitoSugar {

  class Context {
    var out = new ByteArrayOutputStream
    val socket = mock[Socket]

    var collection: StatsCollection = null
    var statsLogger: GraphiteStatsLogger = null

    when(socket.getOutputStream) thenReturn out

    collection = new StatsCollection()
    statsLogger = new GraphiteStatsLogger("localhost", 1123, 1.second, "server_pool", None, collection)

    def getLines() = {
      out.toString.split("\n").toList
    }
  }

  test("log basic stats") {
    val context = new Context
    import context._

    collection.incr("cats")
    collection.incr("dogs", 3)
    statsLogger.write(socket)
    val lines = getLines().sorted
    assert(Pattern.matches("server_pool\\.unknown\\.cats 1\\.00 [0-9]+", lines(0)))
    assert(Pattern.matches("server_pool\\.unknown\\.dogs 3\\.00 [0-9]+", lines(1)))
    verify(socket, times(1)).close
  }

  test("log timings") {
    val context = new Context
    import context._

    Time.withCurrentTimeFrozen { time =>
      collection.time("zzz") { time advance 10.milliseconds }
      collection.time("zzz") { time advance 20.milliseconds }
      statsLogger.write(socket)
      val lines = getLines().sorted
      assert(Pattern.matches("server_pool\\.unknown\\.zzz_msec_average 15\\.00 [0-9]+", lines(0)))
      assert(Pattern.matches("server_pool\\.unknown\\.zzz_msec_p99 19\\.00 [0-9]+", lines(7)))
    }
    verify(socket, times(1)).close
  }

  test("log gauges") {
    val context = new Context
    import context._

    collection.setGauge("horse", 3.5)
    collection.setGauge("cow", 123456789.0)
    statsLogger.write(socket)
    val lines = getLines().sorted
    assert(Pattern.matches("server_pool\\.unknown\\.cow 123456789\\.00 [0-9]+", lines(0)))
    assert(Pattern.matches("server_pool\\.unknown\\.horse 3\\.50 [0-9]+", lines(1)))
    verify(socket, times(1)).close
  }

}
