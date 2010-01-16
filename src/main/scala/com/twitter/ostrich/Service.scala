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

import scala.collection.mutable
import net.lag.configgy.{ConfigMap, RuntimeEnvironment}


trait Service {
  def shutdown(): Unit

  def quiesce(): Unit
}


/**
 * Single server object that can track multiple Service implementations and multiplex the
 * shutdown & quiesce commands.
 */
object ServiceTracker {
  val services = new mutable.HashSet[AdminService]

  def register(service: AdminService) {
    services += service
  }

  def shutdown() {
    services.foreach { _.shutdown() }
    services.clear()
  }

  def quiesce() {
    services.foreach { _.quiesce() }
    services.clear()
  }

  def startAdmin(service: Service, config: ConfigMap, runtime: RuntimeEnvironment) {
    new AdminHttpService(config, runtime).start()
    new AdminSocketService(service, config, runtime).start()
    config.getString("admin_jmx_package").map(StatsMBean(_))
  }
}
