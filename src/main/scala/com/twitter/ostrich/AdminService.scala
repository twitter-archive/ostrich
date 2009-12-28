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

import java.io.IOException
import net.lag.configgy.{Configgy, RuntimeEnvironment}


class UnknownCommandError(command: String) extends IOException("Unknown command: " + command)


/**
 * Common functionality between the admin interfaces.
 */
trait AdminService {
  def runtime: RuntimeEnvironment

  def handleCommand(command: String, parameters: List[String]): Any = {
    command match {
      case "ping" =>
        "pong"
      case "reload" =>
        BackgroundProcess.spawn("admin:reload") { Configgy.reload }
        "ok"
      case "shutdown" =>
        BackgroundProcess.spawn("admin:shutdown") { Server.shutdown() }
        "ok"
      case "quiesce" =>
        BackgroundProcess.spawn("admin:quiesce") { Server.quiesce() }
        "ok"
      case "stats" =>
        val reset = parameters.contains("reset")
        Stats.stats(reset)
      case "server_info" =>
        Map("name" -> runtime.jarName, "version" -> runtime.jarVersion,
            "build" -> runtime.jarBuild, "build_revision" -> runtime.jarBuildRevision)
      case x =>
        throw new UnknownCommandError(x)
    }
  }
}
