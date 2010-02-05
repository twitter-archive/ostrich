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

import java.io.{InputStream, OutputStream}
import java.net.{InetSocketAddress, Socket, URI, URL}
import scala.io.Source
import net.lag.configgy.{Configgy, ConfigMap, RuntimeEnvironment}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}


abstract class CustomHttpHandler extends HttpHandler {
  def render(body: String, exchange: HttpExchange) {
    render(body, exchange, 200)
  }

  def render(body: String, exchange: HttpExchange, code: Int) {
    val input: InputStream = exchange.getRequestBody()
    val output: OutputStream = exchange.getResponseBody()
    exchange.sendResponseHeaders(code, body.length)
    output.write(body.getBytes)
    output.flush()
    output.close()
    exchange.close()
  }

  def handle(exchange: HttpExchange): Unit
}


class MissingFileHandler extends CustomHttpHandler {
  def handle(exchange: HttpExchange) {
    render("no such file", exchange, 404)
  }
}


class ReportRequestHandler extends CustomHttpHandler {
  lazy val pageFilePath: java.net.URI = this.getClass.getResource("/report_request_handler.html").toURI
  lazy val page: String = Source.fromFile(pageFilePath).mkString

  def handle(exchange: HttpExchange) {
    render(page, exchange)
  }
}


class CommandRequestHandler(commandHandler: CommandHandler) extends CustomHttpHandler {
  def handle(exchange: HttpExchange) {
    var response: String = null
    val requestURI = exchange.getRequestURI
    val command = requestURI.getPath.split('/').last.split('.').first

    val format: Format  = requestURI.getPath.split('.').last match {
      case "txt" => Format.PlainText
      case _ => Format.Json
    }

    val parameters: List[String] = {
      val params = requestURI.getQuery

      if (params != null) {
        params.split('&').toList
      } else {
        Nil
      }
    }.map { _.split('=').first }

    try {
      response = {
        val commandResponse = commandHandler(command, parameters, format)

        if (parameters.contains("callback") && (format == Format.Json)) {
          "ostrichCallback(%s)".format(commandResponse)
        } else {
          commandResponse
        }
      }
    } catch {
      case e: UnknownCommandError => render("no such command", exchange, 404)
    } finally {
      render(response, exchange)
    }
  }
}


class AdminHttpService(config: ConfigMap, runtime: RuntimeEnvironment) extends Service {
  val port = config.getInt("admin_http_port")
  val backlog = config.getInt("admin_http_backlog", 20)
  val httpServer: HttpServer = HttpServer.create(new InetSocketAddress(port.get), backlog)
  val commandHandler = new CommandHandler(runtime)

  addContext("/", new CommandRequestHandler(commandHandler))
  addContext("/report/", new ReportRequestHandler())
  addContext("/favicon.ico", new MissingFileHandler())

  httpServer.setExecutor(null)

  def addContext(path: String, handler: HttpHandler) = httpServer.createContext(path, handler)

  def handleRequest(socket: Socket) { }

  def start() = {
    ServiceTracker.register(this)
    httpServer.start()
  }

  override def shutdown() = httpServer.stop(0) // argument is in seconds

  override def quiesce() = httpServer.stop(1) // argument is in seconds
}
