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

import java.net.URL
import scala.collection.immutable
import scala.io.Source
import com.twitter.json.Json
import com.twitter.xrayspecs.Time
import com.twitter.xrayspecs.TimeConversions._
import net.lag.configgy.{Config, RuntimeEnvironment}
import net.lag.extensions._
import org.specs._


object TimeSeriesCollectorSpec extends Specification {
  "TimeSeriesCollector" should {
    var collector: TimeSeriesCollector = null

    doBefore {
      Stats.clearAll()
      collector = new TimeSeriesCollector()
    }

    doAfter {
      collector.shutdown()
    }

    def getJson(port: Int, path: String) = {
      val url = new URL("http://localhost:%d%s".format(port, path))
      Json.parse(Source.fromURL(url).getLines.mkString("\n"))
    }

    "report basic stats" in {
      Time.freeze
      Stats.incr("cats")
      Stats.incr("dogs", 3)
      collector.collector.periodic()
      Time.advance(1.minute)
      Stats.incr("dogs")
      collector.collector.periodic()

      val data = Json.parse(collector.get("dogs_count")).asInstanceOf[Map[String, Seq[Seq[Number]]]]
      data("dogs_count")(57) mustEqual List(2.minutes.ago.inSeconds, 0)
      data("dogs_count")(58) mustEqual List(1.minute.ago.inSeconds, 3)
      data("dogs_count")(59) mustEqual List(Time.now.inSeconds, 1)
    }

    "fetch json via http" in {
      Time.freeze
      Stats.incr("cats")
      Stats.incr("dogs", 3)
      collector.collector.periodic()
      Time.advance(1.minute)
      Stats.incr("dogs")
      collector.collector.periodic()

      val service = new AdminHttpService(new Config(), new RuntimeEnvironment(getClass))
      collector.registerWith(service)
      service.start()
      val port = service.address.getPort
      try {
        val keys = getJson(port, "/graph_data").asInstanceOf[Map[String, Seq[String]]]
        keys("keys") mustContain "dogs_count"
        keys("keys") mustContain "cats_count"
        val data = getJson(port, "/graph_data/dogs_count").asInstanceOf[Map[String, Seq[Seq[Number]]]]
        data("dogs_count")(57) mustEqual List(2.minutes.ago.inSeconds, 0)
        data("dogs_count")(58) mustEqual List(1.minute.ago.inSeconds, 3)
        data("dogs_count")(59) mustEqual List(Time.now.inSeconds, 1)
      } finally {
        service.shutdown()
      }
    }
  }
}
