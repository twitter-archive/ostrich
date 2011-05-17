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
package admin

import java.io.{InputStream, OutputStream}
import java.net.{InetSocketAddress, Socket, URI}
import scala.io.Source
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import com.twitter.conversions.time._
import com.twitter.logging.Logger
import com.twitter.util.{Duration, Time}

/**
 * Custom handler interface for the admin web site. The standard `render` calls are implemented in
 * terms of a single `handle` call. For more functionality, check out subclasses like
 * `FolderResourceHandler` and `CgiRequestHandler`.
 */
abstract class CustomHttpHandler extends HttpHandler {
  private val log = Logger.get(getClass)

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
    log.debug("Loading resource from file: %s", name)
    val stream = getClass.getResourceAsStream(name)
    try {
      Source.fromInputStream(stream).mkString
    } catch {
      case e =>
        log.error(e, "Unable to load Resource from Classpath: %s", name)
        throw e
    }
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

/**
 * Serve static pages as java resources.
 */
class FolderResourceHandler(staticPath: String) extends CustomHttpHandler {
  /**
   * Given a requestPath (e.g. /static/digraph.js), break it up into the path and filename
   */
  def getRelativePath(requestPath: String): String = {
    if (requestPath.startsWith(staticPath)) {
      requestPath.substring(staticPath.length + 1)
    } else {
      requestPath
    }
  }

  def buildPath(relativePath: String) = staticPath + "/" + relativePath

  def handle(exchange: HttpExchange) {
    val requestPath = exchange.getRequestURI().getPath()
    val relativePath = getRelativePath(requestPath)

    val contentType = if (relativePath.endsWith(".js")) {
      "text/javascript"
    } else if (relativePath.endsWith(".html")) {
      "text/html"
    } else if (relativePath.endsWith(".css")) {
      "text/css"
    } else {
      "application/unknown"
    }

    render(loadResource(buildPath(relativePath)), exchange, 200, contentType)
  }
}

object CgiRequestHandler {
  def exchangeToParameters(exchange: HttpExchange): List[(String, String)] =
    Option(exchange.getRequestURI) match {
      case Some(uri) => uriToParameters(uri)
      case None      => Nil
    }

  def uriToParameters(uri: URI): List[(String, String)] = {
    Option(uri.getQuery).getOrElse("").split("&").toList.filter { _.contains("=") }.map { param =>
      param.split("=", 2).toList match {
        case k :: v :: Nil => (k, v)
        case k :: Nil => (k, "")
        case _ => ("", "") // won't happen, but stops the compiler from whining.
      }
    }
  }
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

  def handle(exchange: HttpExchange, path: List[String], parameters: List[(String, String)])
}

class HeapResourceHandler extends CgiRequestHandler {
  private val log = Logger(getClass.getName)
  case class Params(pause: Duration, samplingPeriod: Int, forceGC: Boolean)

  def handle(exchange: HttpExchange, path: List[String], parameters: List[(String, String)]) {
    if (!Heapster.instance.isDefined) {
      render("heapster not loaded!", exchange)
      return
    }
    val heapster = Heapster.instance.get

    val params =
      parameters.foldLeft(Params(10.seconds, 10 << 19, true)) {
        case (params, ("pause", pauseVal)) =>
          params.copy(pause = pauseVal.toInt.seconds)
        case (params, ("sample_period", sampleVal)) =>
          params.copy(samplingPeriod = sampleVal.toInt)
        case (params, ("force_gc", "no")) =>
          params.copy(forceGC = false)
        case (params, ("force_gc", "0")) =>
          params.copy(forceGC = false)
        case (params, _) =>
          params
      }

    log.info("collecting heap profile for %s seconds".format(params.pause))
    val profile = heapster.profile(params.pause, params.samplingPeriod, params.forceGC)

    // Write out the profile verbatim. It's a pprof "raw" profile.
    exchange.getResponseHeaders.set("Content-Type", "pprof/raw")
    exchange.sendResponseHeaders(200, profile.size)
    val output: OutputStream = exchange.getResponseBody()
    output.write(profile)
    output.flush()
    output.close()
    exchange.close()
  }
}

class CommandRequestHandler(commandHandler: CommandHandler) extends CgiRequestHandler {
  def handle(exchange: HttpExchange, path: List[String], parameters: List[(String, String)]) {
    val command = path.last.split('.').head
    val format: Format = path.last.split('.').last match {
      case "txt" => Format.PlainText
      case _ => Format.Json
    }

    val parameterMap = Map(parameters: _*)
    try {
      val response = {
        val commandResponse = commandHandler(command, parameterMap, format)

        if (parameterMap.keySet.contains("callback") && (format == Format.Json)) {
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

class AdminHttpService(port: Int, backlog: Int, runtime: RuntimeEnvironment) extends Service {
  val log = Logger(getClass)
  val httpServer: HttpServer = HttpServer.create(new InetSocketAddress(port), backlog)
  val commandHandler = new CommandHandler(runtime)

  def address = httpServer.getAddress

  addContext("/", new CommandRequestHandler(commandHandler))
  addContext("/report/", new PageResourceHandler("/report_request_handler.html"))
  addContext("/favicon.ico", new MissingFileHandler())
  addContext("/static", new FolderResourceHandler("/static"))
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
    log.info("Admin HTTP interface started on port %d.", port)
  }

  override def shutdown() = httpServer.stop(0) // argument is in seconds

  override def quiesce() = httpServer.stop(1) // argument is in seconds
}
