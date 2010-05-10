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

import scala.collection.{immutable, mutable}
import com.sun.net.httpserver.HttpExchange
import com.twitter.json.Json
import com.twitter.xrayspecs.{Duration, Time}
import com.twitter.xrayspecs.TimeConversions._
import net.lag.logging.Logger


/**
 * Collect stats over a rolling window of the last hour and report them to a web handler,
 * for generating ad-hoc realtime graphs.
 */
class TimeSeriesCollector {
  class TimeSeries(val size: Int) {
    val data = new Array[Double](size)
    var index = 0

    def add(n: Double) {
      data(index) = n
      index = (index + 1) % size
    }

    def toList = {
      val out = new Array[Double](size)
      System.arraycopy(data, index, out, 0, size - index)
      System.arraycopy(data, 0, out, size - index, index)
      out.toList
    }
  }

  val hourly = new mutable.HashMap[String, TimeSeries]()
  var lastCollection: Time = Time(0.seconds)

  val collector = new PeriodicBackgroundProcess("TimeSeriesCollector", 1.minute) {
    val stats = Stats.fork()

    def periodic() {
      Stats.getJvmStats().elements.foreach { case (k, v) =>
        hourly.getOrElseUpdate("jvm_" + k, new TimeSeries(60)).add(v.toDouble)
      }
      Stats.getGaugeStats(true).elements.foreach { case (k, v) =>
        hourly.getOrElseUpdate(k, new TimeSeries(60)).add(v)
      }
      stats.getCounterStats(true).elements.foreach { case (k, v) =>
        hourly.getOrElseUpdate(k + "_count", new TimeSeries(60)).add(v.toDouble)
      }
      stats.getTimingStats(true).elements.foreach { case (k, v) =>
//        hourly.getOrElseUpdate(k + "_count", new TimeSeries(60)).add(v.toDouble)
      }
      lastCollection = Time.now
    }
  }

  def get(name: String) = {
    val times = (for (i <- 0 until 60) yield (lastCollection + (i - 59).minutes).inSeconds).toList
    val data = times.zip(hourly(name).toList).map { case (a, b) => List(a, b) }
    Json.build(immutable.Map(name -> data)).toString
  }

  def keys = hourly.keys

  def registerWith(service: AdminHttpService) {
    service.addContext("/graph_data", new CgiRequestHandler {
      def handle(exchange: HttpExchange, path: List[String], parameters: List[List[String]]) {
        if (path.size == 1) {
          render(Json.build(Map("keys" -> keys.toList)).toString, exchange)
        } else {
          render(get(path.last), exchange)
        }
      }
    })
  }

  def shutdown() {
    collector.shutdown()
  }
}
