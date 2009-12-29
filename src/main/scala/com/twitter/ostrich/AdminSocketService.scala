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
import com.twitter.json.Json
import net.lag.configgy.{Configgy, ConfigMap, RuntimeEnvironment}
import Conversions._


class AdminSocketService(server: ServerInterface, config: ConfigMap, val runtime: RuntimeEnvironment)
      extends AdminService("AdminSocketService", server, runtime) {
  val port = config.getInt("admin_text_port")

  def handleRequest(socket: Socket) {
    var out = new PrintWriter(socket.getOutputStream(), true)
    var in = new BufferedReader(new InputStreamReader(socket.getInputStream))
    val line = in.readLine()
    val request = line.split("\\s+").toList
    val (command, format) = request.head.split("/").toList match {
      case Nil => throw new IOException("impossible")
      case x :: Nil => (x, "text")
      case x :: y :: xs => (x, y)
    }
    val response = handleCommand(command, request.tail)
    format match {
      case "json" => out.print(Json.build(response).toString + "\n")
      case _ => out.print(response.flatten + "\n")
    }
    out.flush()
  }
}
