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

import scala.collection.{immutable, mutable}
import com.sun.net.httpserver.HttpExchange
import com.twitter.conversions.time._
import com.twitter.json.Json
import com.twitter.logging.Logger
import com.twitter.util.{Duration, Time}
import stats._

/**
 * Collect stats over a rolling window of the last hour and report them to a web handler,
 * for generating ad-hoc realtime graphs.
 */
class TimeSeriesCollector(collection: StatsCollection) extends Service {
  val PERCENTILES = List(0.25, 0.5, 0.75, 0.9, 0.95, 0.99, 0.999, 0.9999)
  val EMPTY_TIMINGS = List.fill(PERCENTILES.size)(0L)

  def this() = this(Stats)

  class TimeSeries[T](val size: Int, empty: => T) {
    val data = new mutable.ArrayBuffer[T]()
    for (i <- 0 until size) data += empty
    var index = 0

    def add(n: T) {
      data(index) = n
      index = (index + 1) % size
    }

    def toList: List[T] = {
      val out = new mutable.ListBuffer[T]
      data.drop(index).foreach { out += _ }
      data.slice(0, index).foreach { out += _ }
      out.toList
    }
  }

  val hourly = new mutable.HashMap[String, TimeSeries[Double]]()
  val hourlyTimings = new mutable.HashMap[String, TimeSeries[List[Long]]]()
  var lastCollection: Time = Time.epoch

  val collector = new PeriodicBackgroundProcess("TimeSeriesCollector", 1.minute) {
    val listener = new StatsListener(collection)

    def periodic() {
      val stats = listener.get()
      stats.gauges.foreach { case (k, v) =>
        hourly.getOrElseUpdate("gauge:" + k, new TimeSeries[Double](60, 0)).add(v)
      }
      stats.counters.foreach { case (k, v) =>
        hourly.getOrElseUpdate("counter:" + k, new TimeSeries[Double](60, 0)).add(v.toDouble)
      }
      stats.metrics.foreach { case (k, v) =>
        val data = PERCENTILES.map { percent =>
          v.histogram.getPercentile(percent).toLong
        }
        hourlyTimings.getOrElseUpdate("metric:" + k, new TimeSeries[List[Long]](60, EMPTY_TIMINGS)).add(data)
      }
      lastCollection = Time.now
    }
  }

  def get(name: String, selection: Seq[Int]) = {
    val times = (for (i <- 0 until 60) yield (lastCollection + (i - 59).minutes).inSeconds).toList
    if (hourly.keySet contains name) {
      val data = times.zip(hourly(name).toList).map { case (a, b) => List(a, b) }
      Json.build(immutable.Map(name -> data)).toString + "\n"
    } else {
      val timings = hourlyTimings(name).toList
      val data = times.zip(timings).map { case (a, b) => List(a) ++ b }
      val filteredData = data.map {
        _.zipWithIndex.filter { case (row, index) =>
          selection.isEmpty || index == 0 || (selection contains index - 1)
        }.map { case (row, index) => row }
      }
      Json.build(immutable.Map(name -> filteredData)).toString + "\n"
    }
  }

  def keys = hourly.keys ++ hourlyTimings.keys

  def registerWith(service: AdminHttpService) {
    service.addContext("/graph/", new PageResourceHandler("/graph.html"))
    service.addContext("/graph_data", new CgiRequestHandler {
      def handle(exchange: HttpExchange, path: List[String], parameters: List[(String, String)]) {
        if (path.size == 1) {
          render(Json.build(Map("keys" -> keys.toList)).toString + "\n", exchange)
        } else {
          val keep = parameters.filter { case (k, v) => k == "p" }.headOption.map { case (k, v) =>
            v.split(",").map { _.toInt }
          }.getOrElse((0 until PERCENTILES.size).toArray)
          render(get(path.last, keep), exchange, 200, "application/json")
        }
      }
    })
  }

  def start() {
    collector.thread.setDaemon(true)
    collector.start()
  }

  def shutdown() {
    collector.shutdown()
  }
}
