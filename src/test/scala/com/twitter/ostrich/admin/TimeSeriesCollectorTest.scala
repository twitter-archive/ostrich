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

package com.twitter.ostrich.admin

import com.twitter.conversions.string._
import com.twitter.conversions.time._
import com.twitter.json.Json
import com.twitter.ostrich.stats.Stats
import com.twitter.util.Time
import java.net.URL
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FunSuite, BeforeAndAfter}
import scala.io.Source

@RunWith(classOf[JUnitRunner])
class TimeSeriesCollectorTest extends FunSuite with BeforeAndAfter {

  var collector: TimeSeriesCollector = null

  before {
    Stats.clearAll()
    collector = new TimeSeriesCollector()
  }

  after {
    collector.shutdown()
  }

  def getJson(port: Int, path: String) = {
    val url = new URL("http://localhost:%d%s".format(port, path))
    Json.parse(Source.fromURL(url).getLines.mkString("\n"))
  }

  test("Stats.incr") {
    Time.withCurrentTimeFrozen { time =>
      Stats.incr("cats")
      Stats.incr("dogs", 3)
      collector.collector.periodic()
      time.advance(1.minute)
      Stats.incr("dogs", 60000)
      collector.collector.periodic()

      val json = collector.get("counter:dogs", Nil)
      val data = Json.parse(json).asInstanceOf[Map[String, Seq[Seq[Number]]]]
      assert(data("counter:dogs")(57) === List(2.minutes.ago.inSeconds, 0))
      assert(data("counter:dogs")(58) === List(1.minute.ago.inSeconds, 3))
      assert(data("counter:dogs")(59) === List(Time.now.inSeconds, 60000))
    }
  }

  test("Stats.getCounter().update") {
    Time.withCurrentTimeFrozen { time =>
      Stats.getCounter("whales.tps").incr(10)
      collector.collector.periodic()
      time.advance(1.minute)
      Stats.getCounter("whales.tps").incr(5)
      collector.collector.periodic()

      val json = collector.get("counter:whales.tps", Nil)
      val data = Json.parse(json).asInstanceOf[Map[String, Seq[Seq[Number]]]]
      assert(data("counter:whales.tps")(57) === List(2.minutes.ago.inSeconds, 0))
      assert(data("counter:whales.tps")(58) === List(1.minute.ago.inSeconds, 10))
      assert(data("counter:whales.tps")(59) === List(Time.now.inSeconds, 5))
    }
  }

  test("Stats.getCounter saved in variable") {
    val whales = Stats.getCounter("whales.tps")
    Time.withCurrentTimeFrozen { time =>
      whales.incr(10)
      collector.collector.periodic()
      time.advance(1.minute)
      whales.incr(5)
      collector.collector.periodic()

      val json = collector.get("counter:whales.tps", Nil)
      val data = Json.parse(json).asInstanceOf[Map[String, Seq[Seq[Number]]]]
      assert(data("counter:whales.tps")(57) === List(2.minutes.ago.inSeconds, 0))
      assert(data("counter:whales.tps")(58) === List(1.minute.ago.inSeconds, 10))
      assert(data("counter:whales.tps")(59) === List(Time.now.inSeconds, 5))
    }
  }

  test("fetch json via http") {
    Time.withCurrentTimeFrozen { time =>
      Stats.incr("cats")
      Stats.incr("dogs", 3)
      collector.collector.periodic()
      time.advance(1.minute)
      Stats.incr("dogs", 1)
      collector.collector.periodic()

      val service = new AdminHttpService(0, 20, Stats, new RuntimeEnvironment(getClass))
      collector.registerWith(service)
      service.start()
      val port = service.address.getPort
      try {
        val keys = getJson(port, "/graph_data").asInstanceOf[Map[String, Seq[String]]]
        keys("keys").contains("counter:dogs")
        keys("keys").contains("counter:cats")
        val data = getJson(port, "/graph_data/counter:dogs").asInstanceOf[Map[String, Seq[Seq[Number]]]]
        assert(data("counter:dogs")(57) === List(2.minutes.ago.inSeconds, 0))
        assert(data("counter:dogs")(58) === List(1.minute.ago.inSeconds, 3))
        assert(data("counter:dogs")(59) === List(Time.now.inSeconds, 1))
      } finally {
        service.shutdown()
      }
    }
  }

  test("fetch specific timing percentiles") {
    Time.withCurrentTimeFrozen { time =>
      Stats.addMetric("run", 5)
      Stats.addMetric("run", 10)
      Stats.addMetric("run", 15)
      Stats.addMetric("run", 20)
      collector.collector.periodic()

      val service = new AdminHttpService(0, 20, Stats, new RuntimeEnvironment(getClass))
      collector.registerWith(service)
      service.start()
      val port = service.address.getPort
      try {
        var data = getJson(port, "/graph_data/metric:run").asInstanceOf[Map[String, Seq[Seq[Number]]]]
        assert(data("metric:run")(59) === List(Time.now.inSeconds, 5, 10, 15, 19, 19, 19, 19, 19))
        data = getJson(port, "/graph_data/metric:run?p=0,2").asInstanceOf[Map[String, Seq[Seq[Number]]]]
        assert(data("metric:run")(59) === List(Time.now.inSeconds, 5, 15))
        data = getJson(port, "/graph_data/metric:run?p=1,7").asInstanceOf[Map[String, Seq[Seq[Number]]]]
        assert(data("metric:run")(59) === List(Time.now.inSeconds, 10, 19))
      } finally {
        service.shutdown()
      }
    }
  }

}
