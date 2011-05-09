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

package com.twitter.ostrich
package admin
package config

import scala.collection.Map
import scala.collection.mutable
import com.twitter.conversions.time._
import com.twitter.logging.Logger
import com.twitter.util.{Config, Duration}
import stats._

abstract class StatsReporterConfig extends Config[(StatsCollection, AdminHttpService) => Service]

class JsonStatsLoggerConfig extends StatsReporterConfig {
  var loggerName: String = "stats"
  var period: Duration = 1.minute
  var serviceName: Option[String] = None

  def apply() = { (collection: StatsCollection, admin: AdminHttpService) =>
    new JsonStatsLogger(Logger.get(loggerName), period, serviceName, collection)
  }
}

class W3CStatsLoggerConfig extends StatsReporterConfig {
  var loggerName: String = "w3c"
  var period: Duration = 1.minute

  def apply() = { (collection: StatsCollection, admin: AdminHttpService) =>
    new W3CStatsLogger(Logger.get(loggerName), period, collection)
  }
}

class TimeSeriesCollectorConfig extends StatsReporterConfig {
  def apply() = { (collection: StatsCollection, admin: AdminHttpService) =>
    val service = new TimeSeriesCollector(collection)
    service.registerWith(admin)
    service
  }
}

class StatsConfig extends Config[AdminHttpService => StatsCollection] {
  var name: String = ""
  var reporters: List[StatsReporterConfig] = Nil

  def apply() = { (admin: AdminHttpService) =>
    val collection = Stats.make(name)
    reporters.foreach { reporter =>
      val process = reporter()(collection, admin)
      ServiceTracker.register(process)
      process.start()
    }
    collection
  }
}

class AdminServiceConfig extends Config[RuntimeEnvironment => Option[AdminHttpService]] {
  /**
   * (optional) HTTP port.
   */
  var httpPort: Option[Int] = None

  /**
   * Listen backlog for the HTTP port.
   */
  var httpBacklog: Int = 20

  /**
   * List of configurations for stats nodes.
   * This is where you would define alternate stats collectors, or attach a json or w3c logger.
   */
  var statsNodes: List[StatsConfig] = Nil

  /**
   * Extra handlers for the admin web interface.
   * Each key is a path prefix, and each value is the handler to invoke for that path. You can use
   * this to setup extra functionality for the admin web interface.
   */
  var extraHandlers: Map[String, CustomHttpHandler] = new mutable.HashMap[String, CustomHttpHandler]

  def apply() = { (runtime: RuntimeEnvironment) =>
    httpPort.map { port =>
      val admin = new AdminHttpService(port, httpBacklog, runtime)
      statsNodes.foreach { config =>
        config()(admin)
      }

      admin.start()

      // handlers can't be added until the admin server is started.
      extraHandlers.foreach { case (path, handler) =>
        admin.addContext(path, handler)
      }
      admin
    }
  }
}
