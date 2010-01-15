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
import com.twitter.xrayspecs.Eventually
import net.lag.configgy.{Config, RuntimeEnvironment}
import org.specs.Specification


object AdminHttpServiceSpec extends Specification with Eventually {
  val PORT = 9995
  val config = Config.fromMap(Map("admin_http_port" -> PORT.toString))

  def get(path: String): String = {
    val url = new URL("http://localhost:%s%s".format(PORT, path))
    Source.fromURL(url).getLines.mkString("\n")
  }

  "AdminHttpService" should {
    var service: AdminHttpService = null

    doBefore {
      new Socket("localhost", PORT) must throwA[SocketException] // nothing listening yet
      service = new AdminHttpService(config, new RuntimeEnvironment(getClass))
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
    }

    "shutdown" in {
      get("/shutdown.json")
      new Socket("localhost", PORT) must eventually(throwA[SocketException])
    }

    "quiesce" in {
      get("/quiesce.json")
      new Socket("localhost", PORT) must eventually(throwA[SocketException])
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

      "in text" in {
        // make some statsy things happen
        Stats.clearAll()
        Stats.time("kangaroo_time") { Stats.incr("kangaroos", 1) }

        get("/stats.txt") must beMatching("  kangaroos: 1")
      }
    }
  }
}
