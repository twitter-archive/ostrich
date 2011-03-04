/*
 * Copyright 2010 Twitter, Inc.
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

package com.twitter.admin

import scala.collection.mutable
import com.sun.net.httpserver.{HttpHandler, HttpExchange}

/**
 * Single server object that can track multiple Service implementations and multiplex the
 * shutdown & quiesce commands.
 */
object ServiceTracker {
  private val services = new mutable.HashSet[Service]
  private val queuedAdminHandlers = new mutable.HashMap[String, HttpHandler]
  private var adminHttpService: Option[AdminHttpService] = None

  def clearForTests() {
    services.clear()
  }

  def peek = services.toList

  def register(service: Service) {
    synchronized {
      services += service
    }
  }

  def shutdown() {
    synchronized {
      val rv = services.toList
      services.clear()
      rv
    }.foreach { _.shutdown() }
    stopAdmin()
  }

  def quiesce() {
    synchronized { services.toList }.foreach { _.quiesce() }
    stopAdmin()
  }

  def reload() {
    synchronized { services.toList }.foreach { _.reload() }
  }

  def startAdmin(service: Option[AdminHttpService]) {
    synchronized {
      adminHttpService = service
      service.foreach { s =>
        for ((path, handler) <- queuedAdminHandlers) {
          s.addContext(path, handler)
        }
        s.start()
      }
    }
  }

  def stopAdmin() {
    synchronized {
      adminHttpService.map { _.shutdown() }
      adminHttpService = None
    }
  }

  def registerAdminHttpHandler(path: String)(generator: (List[List[String]]) => String) = {
    val handler = new CustomHttpHandler {
      def handle(exchange: HttpExchange) {
        val parameters = CgiRequestHandler.exchangeToParameters(exchange)
        render(generator(parameters), exchange)
      }
    }

    synchronized {
      adminHttpService match {
        case Some(ahs) => ahs.addContext(path, handler)
        case None      => queuedAdminHandlers(path) = handler
      }
    }
  }
}
