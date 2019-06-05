/*
 * Copyright 2010 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.ostrich.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.conversions.time._
import com.twitter.ostrich.stats.Stats
import com.twitter.util.Time
import java.net.URL
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfter, FunSuite}
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

  def getJson[T](port: Int, path: String, valueType: Class[T]): T = {
    val url = new URL("https://localhost:%d%s".format(port, path))
    val objectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
    objectMapper.readValue(Source.fromURL(url).getLines.mkString("\n"), valueType)
  }

  def getCounterData(name: String): Map[String, Seq[Seq[Number]]] = {
    val json = collector.get(name, Nil)
    val objectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
    objectMapper.readValue(json, classOf[Map[String, Seq[Seq[Number]]]])
  }

  test("Stats.incr") {
    Time.withCurrentTimeFrozen { time =>
      Stats.incr("cats")
      Stats.incr("dogs", 3)
      collector.collector.periodic()
      time.advance(1.minute)
      Stats.incr("dogs", 60000)
      collector.collector.periodic()

      val data = getCounterData("counter:dogs")
      assert(data("counter:dogs")(57) == List(2.minutes.ago.inSeconds, 0))
      assert(data("counter:dogs")(58) == List(1.minute.ago.inSeconds, 3))
      assert(data("counter:dogs")(59) == List(Time.now.inSeconds, 60000))
    }
  }

  test("Stats.getCounter().update") {
    Time.withCurrentTimeFrozen { time =>
      Stats.getCounter("whales.tps").incr(10)
      collector.collector.periodic()
      time.advance(1.minute)
      Stats.getCounter("whales.tps").incr(5)
      collector.collector.periodic()

      val data = getCounterData("counter:whales.tps")
      assert(data("counter:whales.tps")(57) == List(2.minutes.ago.inSeconds, 0))
      assert(data("counter:whales.tps")(58) == List(1.minute.ago.inSeconds, 10))
      assert(data("counter:whales.tps")(59) == List(Time.now.inSeconds, 5))
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

      val data = getCounterData("counter:whales.tps")
      assert(data("counter:whales.tps")(57) == List(2.minutes.ago.inSeconds, 0))
      assert(data("counter:whales.tps")(58) == List(1.minute.ago.inSeconds, 10))
      assert(data("counter:whales.tps")(59) == List(Time.now.inSeconds, 5))
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
        val keys = getJson(port, "/graph_data", classOf[Map[String, Seq[String]]])
        keys("keys").contains("counter:dogs")
        keys("keys").contains("counter:cats")
        val data = getJson(port, "/graph_data/counter:dogs", classOf[Map[String, Seq[Seq[Number]]]])
        assert(data("counter:dogs")(57) == List(2.minutes.ago.inSeconds, 0))
        assert(data("counter:dogs")(58) == List(1.minute.ago.inSeconds, 3))
        assert(data("counter:dogs")(59) == List(Time.now.inSeconds, 1))
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
        var data = getJson(port, "/graph_data/metric:run", classOf[Map[String, Seq[Seq[Number]]]])
        assert(data("metric:run")(59) == List(Time.now.inSeconds, 5, 10, 15, 19, 19, 19, 19, 19))
        data = getJson(port, "/graph_data/metric:run?p=0,2", classOf[Map[String, Seq[Seq[Number]]]])
        assert(data("metric:run")(59) == List(Time.now.inSeconds, 5, 15))
        data = getJson(port, "/graph_data/metric:run?p=1,7", classOf[Map[String, Seq[Seq[Number]]]])
        assert(data("metric:run")(59) == List(Time.now.inSeconds, 10, 19))
      } finally {
        service.shutdown()
      }
    }
  }

  test("pruneStats() counter") {
    Time.withCurrentTimeFrozen { time =>
      Stats.getCounter("whales.tps").incr(10)
      collector.collector.periodic()

      val data = getCounterData("counter:whales.tps")
      assert(data("counter:whales.tps")(58) == List(1.minute.ago.inSeconds, 0))
      assert(data("counter:whales.tps")(59) == List(Time.now.inSeconds, 10))

      time.advance(1.minute)
      Stats.removeCounter("whales.tps")

      collector.collector.periodic()

      try {
        collector.get("counter:whales.tps", Nil)
        fail("Expected counter to be removed by pruneStats()")
      } catch {
        case e: NoSuchElementException => {
          assert(e.getMessage == "key not found: counter:whales.tps")
        }
      }
    }
  }

  test("pruneStats() metric") {
    Time.withCurrentTimeFrozen { time =>
      Stats.addMetric("run", 5)
      collector.collector.periodic()

      val data = getCounterData("metric:run")
      assert(data("metric:run")(59) == List(Time.now.inSeconds, 5, 5, 5, 5, 5, 5, 5, 5))

      time.advance(1.minute)
      Stats.removeMetric("run")

      collector.collector.periodic()

      try {
        collector.get("metric:run", Nil)
        fail("Expected metric to be removed by pruneStats()")
      } catch {
        case e: NoSuchElementException => {
          assert(e.getMessage == "key not found: metric:run")
        }
      }
    }
  }
}
