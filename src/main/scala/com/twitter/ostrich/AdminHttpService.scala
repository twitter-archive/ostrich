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
import net.lag.configgy.{Configgy, ConfigMap, RuntimeEnvironment}
import net.lag.logging.Logger


/**
 * A simple web server that responds to the following paths:
 *
 *   - ping
 *   - reload
 *   - shutdown
 *   - quiesce
 *   - stats
 *   - server_info
 *
 * It can be used from curl like so:
 *
 *     $ curl http://localhost:9990/shutdown
 */
class AdminHttpService(server: ServerInterface, config: ConfigMap, runtime: RuntimeEnvironment)
      extends BackgroundProcess("AdminHttpService") {
  val log = Logger.get(getClass.getName)

  val port = config.getInt("admin_http_port", 9990)
  val serverSocket = new ServerSocket(port)

  serverSocket.setReuseAddress(true)

  Server.register(this)
  Server.register(server)

  private def execute(threadName: String)(f: => Unit) = {
    val t = new Thread(threadName) {
      override def run() {
        f
      }
    }
    t.start()
    t
  }

  def runLoop() {
    val socket = try {
      serverSocket.accept()
    } catch {
      case e: SocketException => throw new InterruptedException()
    }
    execute("AdminHttpService client") {
      try {
        new Client(socket).handleRequest()
      } catch {
        case e: Exception =>
          log.warning("AdminHttpService client %s raised %s", socket, e)
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
    case class Request(command: String, parameters: List[String], format: String)

    private def readRequest(): Request = {
      val in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
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
        sendError("Malformed request line: " + requestLine)
        throw new IOException("Bad request")
      }
      val command = segments(0).toLowerCase()
      if (command != "get") {
        sendError("Request must be GET.")
        throw new IOException("Bad request")
      }
      val pathSegments = segments(1).split("/").filter(_.length > 0)
      if (pathSegments.length < 1) {
        sendError("Malformed request path: " + segments(1))
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

    def handleRequest() {
      val request = readRequest()
      request.command match {
        case "ping" =>
          send("pong")
        case "reload" =>
          send("ok")
          Configgy.reload
        case "shutdown" =>
          send("ok")
          Server.shutdown()
        case "quiesce" =>
          send("ok")
          execute("quiesce request") {
            Thread.sleep(100)
            Server.quiesce()
          }
        case "stats" =>
          val reset = request.parameters.contains("reset")
          request.format match {
            case "txt" =>
              sendRaw(Stats.stats(reset))
            case _ =>
              send(Map("jvm" -> Stats.getJvmStats, "counters" -> Stats.getCounterStats(reset),
                       "timings" -> Stats.getTimingStats(reset), "gauges" -> Stats.getGaugeStats(reset)))
          }
        case "server_info" =>
          send(Map("name" -> runtime.jarName, "version" -> runtime.jarVersion,
                   "build" -> runtime.jarBuild, "build_revision" -> runtime.jarBuildRevision))
        case x =>
          sendError("Unknown command: " + x)
      }
    }

    private def sendError(message: String) {
      log.info("Admin http client error: %s", message)
      send("400", "ERROR", Map("error" -> message))
    }

    private def send(data: Any): Unit = send("200", "OK", data)

    private def send(code: String, codeDescription: String, data: Any): Unit =
      sendRaw(code, codeDescription, Json.build(data).toString + "\n")

    private def sendRaw(data: String): Unit = sendRaw("200", "OK", data)

    private def sendRaw(code: String, codeDescription: String, data: String) {
      val out = new OutputStreamWriter(socket.getOutputStream())
      out.write("HTTP/1.0 %s %s\n".format(code, codeDescription))
      out.write("Server: %s/%s %s %s\n".format(runtime.jarName, runtime.jarVersion,
        runtime.jarBuild, runtime.jarBuildRevision))
      out.write("Content-Type: text/plain\n")
      out.write("\n")
      out.write(data)
      out.flush()
    }
  }
}
