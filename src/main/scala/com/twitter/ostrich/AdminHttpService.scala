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
import java.net.{ServerSocket, Socket, SocketException, SocketTimeoutException}
import java.util.concurrent.CountDownLatch
import com.twitter.json.Json
import net.lag.configgy.{Configgy, RuntimeEnvironment}
import net.lag.logging.Logger


class AdminHttpService(server: ServerInterface, runtime: RuntimeEnvironment) {
  val log = Logger.get

  val port = Configgy.config.getInt("admin_http_port", 9990)
  val serverSocket = new ServerSocket(port)
  var startupLatch: CountDownLatch = null

  serverSocket.setReuseAddress(true)

  private def execute(threadName: String)(f: => Unit) {
    new Thread(threadName) {
      override def run() {
        f
      }
    }.start()
  }

  val thread = new Thread("AdminHttpService") {
    override def run() {
      log.info("Starting admin http service on port %d", port)
      startupLatch.countDown()
      try {
        while (true) {
          val client = serverSocket.accept()
          execute("AdminHttpService client") {
            try {
              handleRequest(client)
            } catch {
              case _ =>
            }
            try {
              client.close()
            } catch {
              case _ =>
            }
          }
        }
      } catch {
        case e: InterruptedException =>
          log.error("Shutting down admin http service.")
          // silently die.
        case e: SocketException =>
          log.error("Shutting down admin http service.")
          // silently die.
        case e: Exception =>
          log.error(e, "AdminHttpService uncaught exception; dying: %s", e.toString)
      }
    }
  }

  case class Request(command: String, parameters: List[String], format: String)

  private def readRequest(client: Socket): Request = {
    val in = new BufferedReader(new InputStreamReader(client.getInputStream()))
    val requestLine = in.readLine()
    if (requestLine == null) {
      throw new IOException("EOF")
    }
    val segments = requestLine.split(" ", 3)
    if (segments.length == 3) {
      // read the "headers", which we will ignore.
      while (in.readLine() != "") { }
    }
    if (segments.length < 2) {
      sendError(client, "Malformed request line: " + requestLine)
      throw new IOException("Bad request")
    }
    val command = segments(0).toLowerCase()
    if (command != "get") {
      sendError(client, "Request must be GET.")
      throw new IOException("Bad request")
    }
    val pathSegments = segments(1).split("/").filter(_.length > 0)
    if (pathSegments.length < 1) {
      sendError(client, "Malformed request path: " + segments(1))
      throw new IOException("Bad request")
    }
    if (pathSegments.last contains ".") {
      val filenameSegments = pathSegments.last.split("\\.", 2)
      val params = pathSegments.slice(0, pathSegments.size - 1) ++ List(filenameSegments(0))
      Request(params(0), params.drop(1).toList, filenameSegments(1).toLowerCase())
    } else {
      Request(pathSegments(0), pathSegments.drop(1).toList, "json")
    }
  }

  def handleRequest(client: Socket) {
    val request = readRequest(client)
    request.command match {
      case "ping" =>
        send(client, "pong")
      case "reload" =>
        send(client, "ok")
        Configgy.reload
      case "shutdown" =>
        send(client, "ok")
        server.shutdown()
      case "quiesce" =>
        send(client, "ok")
        server.quiesce()
        execute("quiesce request") {
          Thread.sleep(100)
        }
      case "stats" =>
        val reset = request.parameters.contains("reset")
        request.format match {
          case "txt" =>
            sendRaw(client, Stats.stats(reset))
          case _ =>
            send(client, Map("jvm" -> Stats.getJvmStats, "counters" -> Stats.getCounterStats(reset),
                             "timings" -> Stats.getTimingStats(reset), "gauges" -> Stats.getGaugeStats(reset)))
        }
      case "server_info" =>
        send(client, Map("name" -> runtime.jarName, "version" -> runtime.jarVersion,
                         "build" -> runtime.jarBuild, "build_revision" -> runtime.jarBuildRevision))
      case x =>
        sendError(client, "Unknown command: " + x)
    }
  }

  private def send(client: Socket, data: Any): Unit = send(client, "200", "OK", data)

  private def sendError(client: Socket, message: String) {
    log.info("Admin http client error: %s", message)
    send(client, "400", "ERROR", Map("error" -> message))
  }

  private def send(client: Socket, code: String, codeDescription: String, data: Any): Unit = sendRaw(client, code, codeDescription, Json.build(data).toString + "\n")

  private def sendRaw(client: Socket, data: String): Unit = sendRaw(client, "200", "OK", data)

  private def sendRaw(client: Socket, code: String, codeDescription: String, data: String) {
    val out = new OutputStreamWriter(client.getOutputStream())
    out.write("HTTP/1.0 %s %s\n".format(code, codeDescription))
    out.write("Server: %s/%s %s %s\n".format(runtime.jarName, runtime.jarVersion, runtime.jarBuild, runtime.jarBuildRevision))
    out.write("Content-Type: text/plain\n")
    out.write("\n")
    out.write(data)
    out.flush()
  }

  def start() {
    startupLatch = new CountDownLatch(1)
    thread.start()
    startupLatch.await()
  }

  def stop() {
    try {
      serverSocket.close()
    } catch {
      case _ =>
    }
    thread.interrupt()
    thread.join()
  }
}
