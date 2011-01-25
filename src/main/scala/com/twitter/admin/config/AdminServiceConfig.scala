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
package config

import com.twitter.config.Config
import com.twitter.conversions.time._
import com.twitter.logging.Logger
import com.twitter.util.Duration
import json.JsonStatsLogger

class AdminServiceConfig extends Config[RuntimeEnvironment => AdminHttpService] {
  /**
   * (optional) HTTP port.
   */
  var httpPort: Option[Int] = None

  /**
   * Listen backlog for the HTTP port.
   */
  var httpBacklog: Int = 20

  /**
   * Turn on the background process that collects an hour of stats for graphing?
   * (This is only meaningful if the HTTP port is active.)
   */
  var collectTimeSeries = true

  /**
   * (optional) Start up a JsonStatsLogger that uses a named log node.
   */
  var jsonStatsLogger: Option[String] = None
  var jsonStatsLoggerPeriod: Duration = 1.minute
  var jsonStatsServiceName: Option[String] = None

  def apply() = { (runtime: RuntimeEnvironment) =>
    val adminHttpService = httpPort.map { port =>
      val service = new AdminHttpService(port, httpBacklog, runtime)

      if (collectTimeSeries) {
        val collector = new TimeSeriesCollector()
        collector.registerWith(service)
        collector.start()
      }

      jsonStatsLogger.map { name =>
        val statsLogger = new JsonStatsLogger(Logger.get(name), jsonStatsLoggerPeriod, jsonStatsServiceName)
        ServiceTracker.register(statsLogger)
        statsLogger.start()
      }
      service
    }

    ServiceTracker.startAdmin(adminHttpService)
    adminHttpService
  }
}
