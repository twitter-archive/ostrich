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
import stats.{JsonStatsLogger, Stats, StatsListener, W3CStatsLogger}
import org.junit.runner.RunWith
import org.mockito.Mockito.{verify, times, when}
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar

@RunWith(classOf[JUnitRunner])
class AdminServiceConfigTest extends FunSuite with BeforeAndAfter with MockitoSugar {

  class Context {
    val port = 9990
    var service: AdminHttpService = null
    val runtime = mock[RuntimeEnvironment]
    when(runtime.arguments) thenReturn Map.empty[String, String]
  }

  before {
    Logger.get("").setLevel(Level.OFF)
  }

  after {
    ServiceTracker.shutdown()
  }

  // Flaky test, see https://jira.twitter.biz/browse/CSL-1004
  if (!sys.props.contains("SKIP_FLAKY"))
    test("start up") {
      val context = new Context
      import context._

      intercept[SocketException] {
        new Socket("localhost", port)
      }

      val config = new AdminServiceConfig {
        httpPort = 9990
      }
      val service = config()(runtime)
      assert(new Socket("localhost", port) !== null)
      ServiceTracker.shutdown()

      intercept[SocketException] {
        new Socket("localhost", port)
      }

      verify(runtime, times(1)).arguments
    }

  // Flaky test, see https://jira.twitter.biz/browse/CSL-1004
  if (!sys.props.contains("SKIP_FLAKY"))
    test("configure a json stats logger") {
      val context = new Context
      import context._

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
      val jsonStatsLoggerConfig = ServiceTracker.peek.find(_.isInstanceOf[JsonStatsLogger])
      assert(jsonStatsLoggerConfig.isDefined &&
        jsonStatsLoggerConfig.get.asInstanceOf[JsonStatsLogger].serviceName === Some("hello"))
      assert(ServiceTracker.peek.find(_.isInstanceOf[TimeSeriesCollector]).isDefined)

      verify(runtime, times(1)).arguments
    }

  // Flaky test, see https://jira.twitter.biz/browse/CSL-1004
  if (!sys.props.contains("SKIP_FLAKY"))
    test("configure a w3c stats logger") {
      val context = new Context
      import context._

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
      val json = Json.parse(data).asInstanceOf[Map[String, Map[String, AnyRef]]]
      assert(json("counters") === Map())
    } finally {
      service.shutdown()
    }

    verify(runtime, times(1)).arguments
  }


}
