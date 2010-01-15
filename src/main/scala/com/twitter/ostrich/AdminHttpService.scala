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
import java.net.{InetSocketAddress, ServerSocket, Socket, SocketException, SocketTimeoutException}
import java.util.concurrent.CountDownLatch
import net.lag.configgy.{Configgy, ConfigMap, RuntimeEnvironment}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}


class RequestHandler extends HttpHandler {
  def handle(exchange: HttpExchange) {
    val input: InputStream = exchange.getRequestBody()

    val requestURI = exchange.getRequestURI
    println("requestURI: " + requestURI)
    println("requestURI.getPath: " + requestURI.getPath)
    println("requestURI.getQuery: " + requestURI.getQuery)

    val command = requestURI.getPath.split('/').last
    println("command: " + command)
    val parameters = requestURI.getQuery.split('&').toList
    println("parameters: " + parameters)

    //val response = handleCommand(command, parameters, Format.Json).getBytes
    val response = "luls"
    println("body: " + response)

    exchange.sendResponseHeaders(200, response.length)

    val output: OutputStream = exchange.getResponseBody()
    output.write(response.getBytes)
    output.flush()
    exchange.close()
  }
}


/**
 * A simple web server that responds to the admin commands defined in `AdminService`.
 * It can be used from curl like so:
 *
 *     $ curl http://localhost:9990/shutdown
 */
class AdminHttpService(config: ConfigMap, runtime: RuntimeEnvironment) extends Service {
  override def port = Some(config.getInt("admin_http_port", 9990))
  val backlog = config.getInt("admin_http_backlog", 20)

  val httpServer: HttpServer = HttpServer.create(new InetSocketAddress(port.get), backlog)
  httpServer.createContext("/", new RequestHandler())
  httpServer.setExecutor(null)

  def handleRequest(socket: Socket) { }

  override def start() = {
    ServiceTracker.register(this)
    httpServer.start()
  }

  override def shutdown() = httpServer.stop(0) // argument is in seconds

  override def quiesce() = httpServer.stop(1) // argument is in seconds
}
