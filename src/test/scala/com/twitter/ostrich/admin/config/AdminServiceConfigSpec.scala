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
package config

import java.net.{Socket, SocketException, URL}
import scala.io.Source
import com.twitter.conversions.time._
import com.twitter.json.Json
import com.twitter.logging.{Level, Logger}
import org.specs.Specification
import org.specs.mock.{ClassMocker, JMocker}
import stats.{JsonStatsLogger, Stats, StatsListener, W3CStatsLogger}

class AdminServiceConfigSpec extends Specification with JMocker with ClassMocker {
  val port = 9990
  var service: AdminHttpService = null
  val runtime = mock[RuntimeEnvironment]

  "AdminServiceConfig" should {
    doBefore {
      Logger.reset()
      Logger.get("").setLevel(Level.OFF)
    }

    doAfter {
      ServiceTracker.shutdown()
    }

    "start up" in {
      expect {
        one(runtime).arguments willReturn Map.empty[String, String]
      }

      new Socket("localhost", port) must throwA[SocketException]
      val config = new AdminServiceConfig {
        httpPort = 9990
      }
      val service = config()(runtime)
      new Socket("localhost", port) must notBeNull
      ServiceTracker.shutdown()
      new Socket("localhost", port) must throwA[SocketException]
    }

    "configure a json stats logger" in {
      expect {
        one(runtime).arguments willReturn Map.empty[String, String]
      }

      val config = new AdminServiceConfig {
        httpPort = 9990
        statsNodes = new StatsConfig {
          reporters = new JsonStatsLoggerConfig {
            loggerName = "json"
            period = 1.second
            serviceName = "hello"
          } :: new TimeSeriesCollectorConfig()
        }
      }
      val service = config()(runtime)
      ServiceTracker.peek must exist { s =>
        s.isInstanceOf[JsonStatsLogger] && s.asInstanceOf[JsonStatsLogger].serviceName == Some("hello")
      }
      ServiceTracker.peek must exist { s =>
        s.isInstanceOf[TimeSeriesCollector]
      }
    }

    "configure filtered stats" in {
      Stats.clearAll()
      StatsListener.clearAll()

      expect {
        one(runtime).arguments willReturn Map.empty[String, String]
      }

      val config = new AdminServiceConfig {
        httpPort = 0
        statsFilters = List("a.*", "jvm_.*")
      }
      val service = config()(runtime).get

      try {
        Stats.incr("apples", 10)
        Stats.addMetric("oranges", 5)

        val port = service.address.getPort
        val path = "/stats.json?period=60&filtered=1"
        val url = new URL("http://localhost:%s%s".format(port, path))
        val data = Source.fromURL(url).getLines().mkString("\n")
        val json = Json.parse(data).asInstanceOf[Map[String, Map[String, AnyRef]]]
        json("counters") mustEqual Map()
      } finally {
        service.shutdown()
      }
    }

    "configure a w3c stats logger" in {
      expect {
        one(runtime).arguments willReturn Map.empty[String, String]
      }

      val config = new AdminServiceConfig {
        httpPort = 9990
        statsNodes = new StatsConfig {
          reporters = new W3CStatsLoggerConfig {
            loggerName = "w3c"
            period = 1.second
          }
        }
      }
      val service = config()(runtime)
      ServiceTracker.peek must exist { s =>
        s.isInstanceOf[W3CStatsLogger] && s.asInstanceOf[W3CStatsLogger].logger.name == "w3c"
      }
    }
  }
}
