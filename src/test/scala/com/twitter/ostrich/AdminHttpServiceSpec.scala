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

import java.io.{DataInputStream, InputStream}
import java.net.{Socket, SocketException, URL}
import scala.io.Source
import com.twitter.json.Json
import net.lag.configgy.{Config, RuntimeEnvironment}
import org.specs.Specification
import org.specs.mock.Mockito


object AdminHttpServiceSpec extends Specification with Mockito {
  val PORT = 9996
  val config = Config.fromMap(Map("admin_http_port" -> PORT.toString))

  def get(path: String): String = {
    val url = new URL("http://localhost:%s%s".format(PORT, path))
    Source.fromURL(url).getLines.mkString("\n")
  }

  "AdminHttpService" should {
    var service: AdminHttpService = null

    doBefore {
      Stats.clearAll()
      new Socket("localhost", PORT) must throwA[SocketException] // nothing listening yet
      service = spy(new AdminHttpService(config, new RuntimeEnvironment(getClass)))
      service.start()
    }

    doAfter {
      service.shutdown()
    }

    "start and stop" in {
      new Socket("localhost", PORT) must notBeNull
      service.shutdown()
      new Socket("localhost", PORT) must throwA[SocketException]
    }

    "answer pings" in {
      val socket = new Socket("localhost", PORT)
      get("/ping.json").trim mustEqual """{"response":"pong"}"""

      service.shutdown()
      new Socket("localhost", PORT) must eventually(throwA[SocketException])
      there was atLeastOne(service).shutdown()
    }

    "shutdown" in {
      get("/shutdown.json")
      new Socket("localhost", PORT) must eventually(throwA[SocketException])
      there was atLeastOne(service).shutdown()
    }

    "quiesce" in {
      get("/quiesce.json")
      new Socket("localhost", PORT) must eventually(throwA[SocketException])
      there was atLeastOne(service).quiesce()
    }

    "get a proper web page back for the report URL" in {
      get("/report/") must beMatching("Stats Report")
    }

    "return 404 for favicon" in {
      get("/favicon.ico") must throwA[java.io.FileNotFoundException]
    }

    "return 404 for a missing command" in {
      get("/bullshit.json") must throwA[java.io.FileNotFoundException]
    }

    "server info" in {
      val serverInfo = get("/server_info.json")
      serverInfo mustMatch("\"build\":")
      serverInfo mustMatch("\"build_revision\":")
      serverInfo mustMatch("\"name\":")
      serverInfo mustMatch("\"version\":")
      serverInfo mustMatch("\"start_time\":")
      serverInfo mustMatch("\"uptime\":")
    }

    "provide stats" in {
      doAfter {
        service.shutdown()
      }

      "in json" in {
        // make some statsy things happen
        Stats.clearAll()
        Stats.time("kangaroo_time") { Stats.incr("kangaroos", 1) }

        val stats = Json.parse(get("/stats.json")).asInstanceOf[Map[String, Map[String, AnyRef]]]
        stats("jvm") must haveKey("uptime")
        stats("jvm") must haveKey("heap_used")
        stats("counters") must haveKey("kangaroos")
        stats("timings") must haveKey("kangaroo_time")

        val timing = stats("timings")("kangaroo_time").asInstanceOf[Map[String, Int]]
        timing("count") mustEqual 1
        timing("average") mustEqual timing("minimum")
        timing("average") mustEqual timing("maximum")
      }

      "in json, with reset" in {
        // make some statsy things happen
        Stats.clearAll()
        Stats.time("kangaroo_time") { Stats.incr("kangaroos", 1) }

        val stats = Json.parse(get("/stats.json?reset=true")).asInstanceOf[Map[String, Map[String, AnyRef]]]
        val timing = stats("timings")("kangaroo_time").asInstanceOf[Map[String, Int]]
        timing("count") mustEqual 1

        val stats2 = Json.parse(get("/stats.json?reset=true")).asInstanceOf[Map[String, Map[String, AnyRef]]]
        val timing2 = stats2("timings")("kangaroo_time").asInstanceOf[Map[String, Int]]
        timing2("count") mustEqual 0
      }

      "in json, with histograms" in {
        Stats.clearAll()
        // Add items indirectly to the histogram
        Stats.addTiming("kangaroo_time", 1)
        Stats.addTiming("kangaroo_time", 2)
        Stats.addTiming("kangaroo_time", 3)
        Stats.addTiming("kangaroo_time", 4)
        Stats.addTiming("kangaroo_time", 5)
        Stats.addTiming("kangaroo_time", 6)


        val stats = get("/stats.json")
        val json = Json.parse(stats).asInstanceOf[Map[String, Map[String, AnyRef]]]
        val timings = json("timings")("kangaroo_time").asInstanceOf[Map[String, Int]]

        timings must haveKey("count")
        timings("count") mustEqual 6

        timings must haveKey("average")
        timings("average") mustEqual 3

        timings must haveKey("standard_deviation")
        timings("standard_deviation") mustEqual 2

        timings must haveKey("p25")
        timings("p25") mustEqual 2

        timings must haveKey("p50")
        timings("p50") mustEqual 3

        timings must haveKey("p75")
        timings("p75")  mustEqual 6

        timings must haveKey("p99")
        timings("p99") mustEqual 6

        timings must haveKey("p999")
        timings("p999") mustEqual 6

        timings must haveKey("p9999")
        timings("p9999") mustEqual 6
      }

      "in json, with histograms and reset" in {
        Stats.clearAll()
        // Add items indirectly to the histogram
        Stats.addTiming("kangaroo_time", 1)
        Stats.addTiming("kangaroo_time", 2)
        Stats.addTiming("kangaroo_time", 3)
        Stats.addTiming("kangaroo_time", 4)
        Stats.addTiming("kangaroo_time", 5)
        Stats.addTiming("kangaroo_time", 6)


        val stats = get("/stats.json?reset")
        val json = Json.parse(stats).asInstanceOf[Map[String, Map[String, AnyRef]]]
        val timings = json("timings")("kangaroo_time").asInstanceOf[Map[String, Int]]

        timings must haveKey("count")
        timings("count") mustEqual 6

        timings must haveKey("average")
        timings("average") mustEqual 3

        timings must haveKey("standard_deviation")
        timings("standard_deviation") mustEqual 2

        timings must haveKey("p25")
        timings("p25") mustEqual 2

        timings must haveKey("p50")
        timings("p50") mustEqual 3

        timings must haveKey("p75")
        timings("p75")  mustEqual 6

        timings must haveKey("p99")
        timings("p99") mustEqual 6

        timings must haveKey("p999")
        timings("p999") mustEqual 6

        timings must haveKey("p9999")
        timings("p9999") mustEqual 6
      }

      "in json, with callback" in {
        val stats = get("/stats.json?callback=true")
        stats.startsWith("ostrichCallback(") mustBe true
        stats.endsWith(")") mustBe true
      }

      "in text" in {
        // make some statsy things happen
        Stats.clearAll()
        Stats.time("kangaroo_time") { Stats.incr("kangaroos", 1) }

        get("/stats.txt") must beMatching("  kangaroos: 1")
      }
    }
  }
}
