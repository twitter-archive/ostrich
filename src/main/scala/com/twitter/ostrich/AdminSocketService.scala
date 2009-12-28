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

import java.io._
import java.net._
import com.twitter.json.Json
import net.lag.configgy.{Configgy, ConfigMap, RuntimeEnvironment}
import net.lag.logging.Logger
import Conversions._


class AdminSocketService(server: ServerInterface, config: ConfigMap, val runtime: RuntimeEnvironment)
  extends BackgroundProcess("AdminSocketService") with AdminService {

  val log = Logger.get(getClass.getName)

  val port = config.getInt("admin_text_port", 9989)
  val serverSocket = new ServerSocket(port)

  serverSocket.setReuseAddress(true)

  Server.register(this)
  Server.register(server)

  def runLoop() {
    val socket = try {
      serverSocket.accept()
    } catch {
      case e: SocketException => throw new InterruptedException()
    }
    BackgroundProcess.spawn("AdminSocketService client") {
      try {
        new Client(socket).handleRequest()
      } catch {
        case e: Exception =>
          log.warning("AdminSocketService client %s raised %s", socket, e)
      } finally {
        try {
          socket.close()
        } catch {
          case _ =>
        }
      }
    }
  }

  override def shutdown() {
    try {
      serverSocket.close()
    } catch {
      case _ =>
    }
    super.shutdown()
  }

  class Client(val socket: Socket) {
    socket.setSoTimeout(1000)

    def handleRequest() {
      log.debug("Client has connected to AdminSocketService from %s:%s", socket.getInetAddress, socket.getPort)
      var out: PrintWriter = null
      var in: BufferedReader = null
      try {
        out = new PrintWriter(socket.getOutputStream(), true)
        in = new BufferedReader(new InputStreamReader(socket.getInputStream))
        val line = in.readLine()
        val request = line.split("\\s+").toList
        val (command, format) = request.head.split("/").toList match {
          case Nil => throw new IOException("impossible")
          case x :: Nil => (x, "text")
          case x :: y :: xs => (x, y)
        }
        val response = handleCommand(command, request.tail)
        format match {
          case "json" => out.print(Json.build(response).toString + "\n")
          case _ => out.print(response.flatten + "\n")
        }
        out.flush()
      } catch {
        case e: IOException =>
          log.error(e, "Error writing to AdminSocketService client")
      } finally {
        out.close()
        in.close()
        socket.close()
      }
    }
  }
}