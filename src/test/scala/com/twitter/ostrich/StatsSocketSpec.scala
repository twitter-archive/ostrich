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

import net.lag.extensions._
import org.specs._
import scala.collection.immutable
import java.net.{Socket, SocketException}
import java.io.{BufferedReader, InputStreamReader, PrintWriter}


object StatsSocketSpec extends Specification {
  def fn(cmd: String): String = cmd match {
    case "stats text" => "foo: 1\n"
    case "stats json" => "{\"foo\": 1}\n"
    case cmd => "error unknown command: " + cmd + "\n"
  }

  "StatsSocket" should {
    var listener: StatsSocketListener = null
    var server: Thread = null
    var clientSocket: Socket = null
    var out: PrintWriter = null
    var in: BufferedReader = null

    doBefore {
      if (listener != null) { listener.stop }
      if (server != null) { server.stop }

      listener = new StatsSocketListener(43210, 10000, fn)
      server = new Thread(listener)
      server.start

      clientSocket = try {
        new Socket("localhost", 43210)
      } catch {
        case e: Exception => fail("unable to connect to port 43210"); null
      }
      out = new PrintWriter(clientSocket.getOutputStream, true)
      in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream))
    }

    doAfter {
      try {
        listener.stop()
      } catch {
        case e: Exception =>
      }
    }

    "return text content correctly and close" >> {
      out.println("stats text")
      out.flush()
      in.readLine() mustEqual "foo: 1"

      out.println("stats text")
      out.flush()
      in.readLine() mustEqual ""
      in.readLine() must throwA[SocketException]
    }

    "return json content correctly and close" >> {
      out.println("stats json")
      out.flush()
      in.readLine() mustEqual "{\"foo\": 1}"

      out.println("stats json")
      out.flush()
      in.readLine() mustEqual ""
      in.readLine() must throwA[SocketException]
    }
  }
}
