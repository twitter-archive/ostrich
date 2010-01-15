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

import java.io.InputStream
import java.net.{Socket, SocketException}
import com.twitter.json.Json
import com.twitter.xrayspecs.Eventually
import net.lag.configgy.{Config, RuntimeEnvironment}
import org.mockito.Matchers._
import org.specs.Specification
import org.specs.mock.Mockito


object AdminSocketServiceSpec extends Specification with Mockito with Eventually {
  val PORT = 9995
  val config = Config.fromMap(Map("admin_text_port" -> PORT.toString))

  class PimpedInputStream(stream: InputStream) {
    def readString(maxBytes: Int) = {
      val buffer = new Array[Byte](maxBytes)
      val len = stream.read(buffer)
      new String(buffer, 0, len, "UTF-8")
    }
  }
  implicit def pimpInputStream(stream: InputStream) = new PimpedInputStream(stream)

  "AdminSocketService" should {
    var service: AdminSocketService = null

    doBefore {
      new Socket("localhost", PORT) must throwA[SocketException]
      service = spy(new AdminSocketService(config, new RuntimeEnvironment(getClass)))
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
      socket.getOutputStream().write("ping\n".getBytes)
      socket.getInputStream().readString(1024) mustEqual "pong\n"

      service.shutdown()
      new Socket("localhost", PORT) must eventually(throwA[SocketException])
      service.shutdown() was called
    }

    "shutdown" in {
      val socket = new Socket("localhost", PORT)
      socket.getOutputStream().write("shutdown\n".getBytes)
      new Socket("localhost", PORT) must eventually(throwA[SocketException])
      service.shutdown() was called
    }

    "quiesce" in {
      val socket = new Socket("localhost", PORT)
      socket.getOutputStream().write("quiesce\n".getBytes)
      new Socket("localhost", PORT) must eventually(throwA[SocketException])
      service.quiesce() was called
    }

    "provide stats" in {
      doAfter {
        service.shutdown()
      }

      "in json" in {
        // make some statsy things happen
        Stats.clearAll()
        Stats.time("kangaroo_time") { Stats.incr("kangaroos", 1) }

        val socket = new Socket("localhost", PORT)
        socket.getOutputStream().write("stats/json\n".getBytes)

        val stats = Json.parse(socket.getInputStream().readString(1024)).asInstanceOf[Map[String, Map[String, AnyRef]]]
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

        val socket = new Socket("localhost", PORT)
        socket.getOutputStream().write("stats/json reset\n".getBytes)
        val stats = Json.parse(socket.getInputStream().readString(1024)).asInstanceOf[Map[String, Map[String, AnyRef]]]
        val timing = stats("timings")("kangaroo_time").asInstanceOf[Map[String, Int]]
        timing("count") mustEqual 1

        val socket2 = new Socket("localhost", PORT)
        socket2.getOutputStream().write("stats/json reset\n".getBytes)
        val stats2 = Json.parse(socket2.getInputStream().readString(1024)).asInstanceOf[Map[String, Map[String, AnyRef]]]
        val timing2 = stats2("timings")("kangaroo_time").asInstanceOf[Map[String, Int]]
        timing2("count") mustEqual 0
      }

      "in text" in {
        // make some statsy things happen
        Stats.clearAll()
        Stats.time("kangaroo_time") { Stats.incr("kangaroos", 1) }

        val socket = new Socket("localhost", PORT)
        socket.getOutputStream().write("stats\n".getBytes)

        val response = socket.getInputStream().readString(1024).split("\n")
        response mustContain "  kangaroos: 1"
      }
    }
  }
}
