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
import net.lag.logging.Logger
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import com.twitter.xrayspecs.TimeConversions._

abstract class CustomHttpHandler extends HttpHandler {
  def render(body: String, exchange: HttpExchange) {
    render(body, exchange, 200)
  }

  def render(body: String, exchange: HttpExchange, code: Int) {
    render(body, exchange, code, "text/html")
  }

  def render(body: String, exchange: HttpExchange, code: Int, contentType: String) {
    val input: InputStream = exchange.getRequestBody()
    val output: OutputStream = exchange.getResponseBody()
    exchange.getResponseHeaders.set("Content-Type", contentType)
    val data = body.getBytes
    exchange.sendResponseHeaders(code, data.size)
    output.write(data)
    output.flush()
    output.close()
    exchange.close()
  }

  def loadResource(name: String) = {
    Source.fromInputStream(getClass.getResourceAsStream(name)).mkString
  }

  def handle(exchange: HttpExchange): Unit
}


class MissingFileHandler extends CustomHttpHandler {
  def handle(exchange: HttpExchange) {
    render("no such file", exchange, 404)
  }
}


class PageResourceHandler(path: String) extends CustomHttpHandler {
  lazy val page = loadResource(path)

  def handle(exchange: HttpExchange) {
    render(page, exchange)
  }
}

class FolderResourceHandler(staticPath: String) extends CustomHttpHandler {

  /**
   * Given a requestPath (e.g. /static/digraph.js), break it up into the path and filename
   */
  def getRelativePath(requestPath: String): String = {
    val n = requestPath.lastIndexOf('/')
    if (requestPath.startsWith(staticPath)) {
      requestPath.substring(staticPath.length)
    } else {
      requestPath
    }
  }

  def handle(exchange: HttpExchange) {
    val requestPath = exchange.getRequestURI().getPath()
    val relativePath = getRelativePath(requestPath)

    val contentType = if (relativePath.endsWith(".js")) {
      "text/javascript"
    } else if (relativePath.endsWith(".html")) {
      "text/html"
    } else {
      "application/unknown"
    }

    render(loadResource(staticPath + "/" + relativePath), exchange, 200, contentType)
  }
}

object CgiRequestHandler {
  def exchangeToParameters(exchange: HttpExchange): List[List[String]] = {
    val params = exchange.getRequestURI.getQuery

    if (params != null) {
      params.split('&').toList
    } else {
      Nil
    }
  }.map { _.split("=", 2).toList }
}

abstract class CgiRequestHandler extends CustomHttpHandler {
  import CgiRequestHandler._

  private val log = Logger(getClass.getName)

  def handle(exchange: HttpExchange) {
    try {
      val requestURI = exchange.getRequestURI
      val path       = requestURI.getPath.split('/').toList.filter { _.length > 0 }
      val parameters = exchangeToParameters(exchange)

      handle(exchange, path, parameters)
    } catch {
      case e =>
        render("exception while processing request: " + e, exchange, 500)
        log.error(e, "Exception processing admin http request")
    }
  }

  def handle(exchange: HttpExchange, path: List[String], parameters: List[List[String]])
}


class HeapResourceHandler extends CgiRequestHandler {
  private val log = Logger(getClass.getName)

  def handle(exchange: HttpExchange, path: List[String], parameters: List[List[String]]) {
    if (!Heapster.instance.isDefined) {
      render("heapster not loaded!", exchange)
      return
    }
    val heapster = Heapster.instance.get

    parameters.filter(_(0) == "pause").firstOption.map(_(1)) match {
      case Some(pause) =>
       log.info("collecting heap profile for %s seconds".format(pause))
       val profile = heapster.profile(pause.toInt.seconds)

       // Write out the profile verbatim. It's a pprof "raw" profile.
       exchange.sendResponseHeaders(200, profile.size)
       val output: OutputStream = exchange.getResponseBody()
       output.write(profile)
       output.flush()
       output.close()
       exchange.close()

      case None =>
        render("failed to parse pause parameter", exchange)
    }
  }
}

class CommandRequestHandler(commandHandler: CommandHandler) extends CgiRequestHandler {
  def handle(exchange: HttpExchange, path: List[String], parameters: List[List[String]]) {
    val command = path.last.split('.').first
    val format: Format = path.last.split('.').last match {
      case "txt" => Format.PlainText
      case _ => Format.Json
    }

    try {
      val response = {
        val parameterNames = parameters.map { p => p(0) }
        val commandResponse = commandHandler(command, parameterNames, format)

        if (parameterNames.contains("callback") && (format == Format.Json)) {
          "ostrichCallback(%s)".format(commandResponse)
        } else {
          commandResponse
        }
      }

      val contentType = if (format == Format.PlainText) "text/plain" else "application/json"
      render(response, exchange, 200, contentType)
    } catch {
      case e: UnknownCommandError => render("no such command", exchange, 404)
      case unknownException =>
        render("error processing command: " + unknownException, exchange, 500)
        unknownException.printStackTrace()
    }
  }
}


class AdminHttpService(config: ConfigMap, runtime: RuntimeEnvironment) extends Service {
  val port = config.getInt("admin_http_port")
  val backlog = config.getInt("admin_http_backlog", 20)
  val httpServer: HttpServer = HttpServer.create(new InetSocketAddress(port.getOrElse(0)), backlog)
  val commandHandler = new CommandHandler(runtime)

  def address = httpServer.getAddress

  addContext("/", new CommandRequestHandler(commandHandler))
  addContext("/report/", new PageResourceHandler("/report_request_handler.html"))
  addContext("/favicon.ico", new MissingFileHandler())
  addContext("/static/", new FolderResourceHandler("/static"))
  addContext("/pprof/heap", new HeapResourceHandler)

  httpServer.setExecutor(null)

  def addContext(path: String, handler: HttpHandler) = httpServer.createContext(path, handler)

  def addContext(path: String)(generator: () => String) = {
    val handler = new CustomHttpHandler {
      def handle(exchange: HttpExchange) {
        render(generator(), exchange)
      }
    }
    httpServer.createContext(path, handler)
  }

  def handleRequest(socket: Socket) { }

  def start() = {
    ServiceTracker.register(this)
    httpServer.start()
  }

  override def shutdown() = httpServer.stop(0) // argument is in seconds

  override def quiesce() = httpServer.stop(1) // argument is in seconds
}
