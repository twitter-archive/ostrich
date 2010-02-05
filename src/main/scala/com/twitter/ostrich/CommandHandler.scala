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


class CommandHandler(runtime: RuntimeEnvironment) {
  def apply(command: String, parameters: List[String], format: Format): String = {
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
        BackgroundProcess.spawn("admin:shutdown") {
          Thread.sleep(100)
          ServiceTracker.shutdown()
        }
        "ok"
      case "quiesce" =>
        BackgroundProcess.spawn("admin:quiesce") {
          Thread.sleep(100)
          ServiceTracker.quiesce()
        }
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
