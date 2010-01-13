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
import scala.collection.Map
import scala.collection.immutable
import com.twitter.json.Json
import net.lag.configgy.{Configgy, RuntimeEnvironment}
import net.lag.logging.Logger
import Conversions._


class UnknownCommandError(command: String) extends IOException("Unknown command: " + command)


sealed abstract case class Format()
object Format {
  case object PlainText extends Format
  case object Json extends Format
}


/**
 * Common functionality between the admin interfaces.
 */
abstract class AdminService(name: String, server: ServerInterface, runtime: RuntimeEnvironment) extends BackgroundProcess(name) {
  def port: Option[Int]
  def handleRequest(socket: Socket): Unit

  val log = Logger.get(getClass.getName)

  var serverSocket: Option[ServerSocket] = None

  override def start() {
    port map { port =>
      serverSocket = Some(new ServerSocket(port))
      serverSocket.map { _.setReuseAddress(true) }
      Server.register(this)
      Server.register(server)
      super.start()
    }
  }

  override def shutdown() {
    try {
      serverSocket.map { _.close() }
    } catch {
      case _ =>
    }
    super.shutdown()
  }

  def runLoop() {
    val socket = try {
      serverSocket.get.accept()
    } catch {
      case e: SocketException =>
        throw new InterruptedException()
    }
    val address = "%s:%s".format(socket.getInetAddress, socket.getPort)
    log.debug("Client has connected to %s from %s", name, address)
    BackgroundProcess.spawn("%s client %s".format(name, address)) {
      try {
        socket.setSoTimeout(1000)
        handleRequest(socket)
      } catch {
        case e: IOException =>
          log.debug("%s client %s raised %s", name, address, e)
        case e: Exception =>
          log.warning("%s client %s raised %s", name, address, e)
      } finally {
        try {
          socket.close()
        } catch {
          case _ =>
        }
      }
    }
  }

  def handleCommand(command: String, parameters: List[String], format: Format): String = {
    val rv = handleRawCommand(command, parameters)
    format match {
      case Format.PlainText =>
        rv.flatten
      case Format.Json =>
        // force it into a map because some json clients expect the top-level object to be a map.
        Json.build(rv match {
          case x: Map[_, _] => x
          case _ => immutable.Map("response" -> rv)
        }).toString + "\n"
    }
  }

  def handleRawCommand(command: String, parameters: List[String]): Any = {
    command match {
      case "ping" =>
        "pong"
      case "reload" =>
        BackgroundProcess.spawn("admin:reload") { Configgy.reload }
        "ok"
      case "shutdown" =>
        BackgroundProcess.spawn("admin:shutdown") { Thread.sleep(100); Server.shutdown() }
        "ok"
      case "quiesce" =>
        BackgroundProcess.spawn("admin:quiesce") { Thread.sleep(100); Server.quiesce() }
        "ok"
      case "stats" =>
        val reset = parameters.contains("reset")
        Stats.stats(reset)
      case "server_info" =>
        immutable.Map("name" -> runtime.jarName, "version" -> runtime.jarVersion,
                      "build" -> runtime.jarBuild, "build_revision" -> runtime.jarBuildRevision)
      case x =>
        throw new UnknownCommandError(x)
    }
  }
}
