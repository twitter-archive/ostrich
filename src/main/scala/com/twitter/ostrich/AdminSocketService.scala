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
import scala.concurrent.ops._
import com.twitter.json.Json
import net.lag.configgy.{Configgy, ConfigMap, RuntimeEnvironment}
import net.lag.logging.Logger


class AdminSocketService(config: ConfigMap, val runtime: RuntimeEnvironment) extends Service {
  val port = config.getInt("admin_text_port")
  private val log = Logger.get(getClass.getName)
  var serverSocket: Option[ServerSocket] = None

  override def start() {
    port map { port =>
      serverSocket = Some(new ServerSocket(port))
      serverSocket.map { _.setReuseAddress(true) }
      ServiceTracker.register(this)
    }
  }

  override def shutdown() {
    try {
      serverSocket.map { _.close() }
    } catch {
      case _ =>
    }
  }

  override def quiesce() {
    Thread.sleep(100)
    shutdown()
  }

  def runLoop() {
    val socket = try {
      serverSocket.get.accept()
    } catch {
      case e: SocketException =>
        throw new InterruptedException()
    }
    val address = "%s:%s".format(socket.getInetAddress, socket.getPort)
    log.debug("Client has connected to socket service from %s", address)

    spawn {
      try {
        socket.setSoTimeout(1000)
        handleRequest(socket)
      } catch {
        case e: Exception =>
          log.warning("socket service client %s raised %s", address, e)
      } finally {
        try {
          socket.close()
        } catch {
          case _ =>
        }
      }
    }
  }

  def handleRequest(socket: Socket) {
    var out = new PrintWriter(socket.getOutputStream(), true)
    var in = new BufferedReader(new InputStreamReader(socket.getInputStream))
    val line = in.readLine()
    val request = line.split("\\s+").toList

    val (command, textFormat) = request.head.split("/").toList match {
      case Nil => throw new IOException("impossible")
      case x :: Nil => (x, "text")
      case x :: y :: xs => (x, y)
    }

    val format = textFormat match {
      case "json" => Format.Json
      case _ => Format.PlainText
    }

    val response = CommandHandler.handleCommand(command, request.tail, format)
    out.println(response)
    out.flush()
  }
}
