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

package com.twitter.ostrich.admin.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.conversions.time._
import com.twitter.logging.{Level, Logger}
import com.twitter.ostrich.admin.{RuntimeEnvironment, ServerInfoHandler, ServiceTracker, TimeSeriesCollector}
import com.twitter.ostrich.stats.{JsonStatsLogger, Stats, StatsListener, W3CStatsLogger}
import java.net.URL
import org.junit.runner.RunWith
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite}
import scala.io.Source

@RunWith(classOf[JUnitRunner])
class AdminServiceConfigTest extends FunSuite with BeforeAndAfter with MockitoSugar {

  class Context {
    val runtime = mock[RuntimeEnvironment]
    val serverInfo = new ServerInfoHandler(new Object)
    when(runtime.arguments).thenReturn(Map.empty[String, String])
    when(runtime.serverInfo).thenReturn(serverInfo)
  }

  before {
    Logger.get("").setLevel(Level.OFF)
  }

  after {
    ServiceTracker.shutdown()
  }

  test("configure a json stats logger") {
    val context = new Context
    import context._

    val config = new AdminServiceConfig {
      httpPort = Some(0)
      statsNodes = new StatsConfig {
        reporters = new JsonStatsLoggerConfig {
          loggerName = "json"
          period = 1.second
          serviceName = "hello"
        } :: new TimeSeriesCollectorConfig()
      }
    }

    val service = config()(runtime)
    val jsonStatsLoggerConfig = ServiceTracker.peek.find(_.isInstanceOf[JsonStatsLogger])
    assert(jsonStatsLoggerConfig.isDefined &&
      jsonStatsLoggerConfig.get.asInstanceOf[JsonStatsLogger].serviceName == Some("hello"))
    assert(ServiceTracker.peek.find(_.isInstanceOf[TimeSeriesCollector]).isDefined)

    verify(runtime, times(1)).arguments
  }

  test("configure a w3c stats logger") {
    val context = new Context
    import context._

    val config = new AdminServiceConfig {
      httpPort = Some(0)
      statsNodes = new StatsConfig {
        reporters = new W3CStatsLoggerConfig {
          loggerName = "w3c"
          period = 1.second
        }
      }
    }
    val service = config()(runtime)
    val w3cStatsLoggerConfig = ServiceTracker.peek.find(_.isInstanceOf[W3CStatsLogger])
    assert(w3cStatsLoggerConfig.isDefined &&
      w3cStatsLoggerConfig.get.asInstanceOf[W3CStatsLogger].logger.name == "w3c")

    verify(runtime, times(1)).arguments
  }

  test("configure filtered stats") {
    val context = new Context
    import context._

    Stats.clearAll()
    StatsListener.clearAll()

    val config = new AdminServiceConfig {
      httpPort = 0
      statsFilters = List("a.*".r, "jvm_.*".r)
    }
    val service = config()(runtime).get

    try {
      Stats.incr("apples", 10)
      Stats.addMetric("oranges", 5)

      val port = service.address.getPort
      val path = "/stats.json?period=60&filtered=1"
      val url = new URL("http://localhost:%s%s".format(port, path))
      val data = Source.fromURL(url).getLines().mkString("\n")
      val mapper = new ObjectMapper().registerModule(DefaultScalaModule)
      val json = mapper.readValue(data, classOf[Map[String, Map[String, AnyRef]]])
      assert(json("counters") == Map.empty[String, Map[String, AnyRef]])
    } finally {
      service.shutdown()
    }

    verify(runtime, times(1)).arguments
  }
}
