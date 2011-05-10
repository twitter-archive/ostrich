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
package stats

import scala.collection.{immutable, mutable}
import com.twitter.conversions.string._
import com.twitter.conversions.time._
import com.twitter.logging.{Level, Logger}
import com.twitter.util.{Time, Future}
import org.specs.Specification

object StatsCollectionSpec extends Specification {
  "StatsCollection" should {
    val collection = new StatsCollection()

    doBefore {
      Logger.get("").setLevel(Level.OFF)
    }

    "fillInJvmGauges" in {
      val map = new mutable.HashMap[String, Double]
      collection.fillInJvmGauges(map)
      map.keys.toList must contain("jvm_num_cpus")
      map.keys.toList must contain("jvm_heap_used")
      map.keys.toList must contain("jvm_start_time")
      map.keys.toList must contain("jvm_post_gc_used")
    }

    "fillInJvmCounters" in {
      val map = new mutable.HashMap[String, Long]
      collection.fillInJvmCounters(map)
      map.keys.toList must contain("jvm_gc_cycles")
      map.keys.toList must contain("jvm_gc_msec")
    }

    "counters" in {
      "basic" in {
        collection.incr("widgets", 1)
        collection.incr("wodgets", 12)
        collection.incr("wodgets")
        collection.getCounters() mustEqual Map("widgets" -> 1, "wodgets" -> 13)
      }

      "negative" in {
        collection.incr("widgets", 3)
        collection.incr("widgets", -1)
        collection.getCounters() mustEqual Map("widgets" -> 2)
      }
    }

    "metrics" in {
      "empty" in {
        collection.addMetric("test", 0)
        val test = collection.getMetric("test")
        test(true) mustEqual new Distribution(1, 0, 0, 0.0)
        // the timings list will be empty here:
        test(true) mustEqual new Distribution(0, 0, 0, 0.0)
      }

      "basic min/max/average" in {
        collection.addMetric("test", 1)
        collection.addMetric("test", 2)
        collection.addMetric("test", 3)
        val test = collection.getMetric("test")
        test(true) mustEqual new Distribution(3, 3, 1, Some(Histogram(1, 2, 3)), 2.0)
      }

      "report" in {
        var x = 0
        collection.time("hundred") { for (i <- 0 until 100) x += i }
        val timings = collection.getMetrics()
        timings.keys.toList mustEqual List("hundred_msec")
        timings("hundred_msec").count mustEqual 1
        timings("hundred_msec").minimum mustEqual timings("hundred_msec").average
        timings("hundred_msec").maximum mustEqual timings("hundred_msec").average
      }

      "time future" in {
        val stat = "latency"
        val future = Future({ Thread.sleep(10); 100 })

        collection.timeFutureMillis(stat)(future)() mustEqual 100
      }

      "average of 0" in {
        collection.addMetric("test", 0)
        val test = collection.getMetric("test")
        test(true) mustEqual new Distribution(1, 0, 0, 0.0)
      }

      "ignore negative timings" in {
        collection.addMetric("test", 1)
        collection.addMetric("test", -1)
        collection.addMetric("test", Int.MinValue)
        val test = collection.getMetric("test")
        test(true) mustEqual new Distribution(1, 1, 1, Some(Histogram(1)), 1.0)
      }

      "boundary timing sizes" in {
        collection.addMetric("test", Int.MaxValue)
        collection.addMetric("test", 5)
        val sum = 5.0 + Int.MaxValue
        val avg = sum / 2.0
        val test = collection.getMetric("test")
        test(true) mustEqual
          new Distribution(2, Int.MaxValue, 5, Some(Histogram(5, Int.MaxValue)), avg)
      }

      "handle code blocks" in {
        Time.withCurrentTimeFrozen { time =>
          collection.time("test") {
            time.advance(10.millis)
          }
          val test = collection.getMetric("test_msec")
          test(true).average must be_>=(10.0)
        }
      }

      "reset when asked" in {
        var x = 0
        collection.time("hundred") { for (i <- 0 until 100) x += i }
        collection.getMetric("hundred_msec")(false).count mustEqual 1
        collection.time("hundred") { for (i <- 0 until 100) x += i }
        collection.getMetric("hundred_msec")(false).count mustEqual 2
        collection.getMetric("hundred_msec")(true).count mustEqual 2
        collection.time("hundred") { for (i <- 0 until 100) x += i }
        collection.getMetric("hundred_msec")(true).count mustEqual 1
      }

      "add bundle of timings at once" in {
        val timingStat = new Distribution(3, 20, 10, Some(Histogram(10, 15, 20)), 15.0)
        collection.addMetric("test", timingStat)
        collection.addMetric("test", 25)
        collection.getMetric("test")(true) mustEqual Distribution(4, 25, 10, None, 17.5)
      }

      "add multiple bundles of timings" in {
        val timingStat1 = new Distribution(2, 25, 15, Some(Histogram(15, 25)), 20.0)
        val timingStat2 = new Distribution(2, 20, 10, Some(Histogram(10, 20)), 15.0)
        collection.addMetric("test", timingStat1)
        collection.addMetric("test", timingStat2)
        collection.getMetric("test")(true) mustEqual Distribution(4, 25, 10, None, 17.5)
      }

      "timing stats can be added and reflected in Stats.getMetrics" in {
        Stats.addMetric("foobar", new Distribution(1, 0, 0, 0.0))
        Stats.getMetrics()("foobar").count mustEqual 1
        Stats.addMetric("foobar", new Distribution(3, 0, 0, 0.0))
        Stats.getMetrics()("foobar").count mustEqual 3
      }

      "report text in sorted order" in {
        Stats.addMetric("alpha", new Distribution(1, 0, 0, 0.0))
        Stats.getMetrics()("alpha").toString mustEqual
          "(average=0, count=1, maximum=0, minimum=0, " +
          "p25=0, p50=0, p75=0, p90=0, p99=0, p999=0, p9999=0)"
      }

      "json contains histogram buckets" in {
        Stats.addMetric("alpha", new Distribution(1, 0, 0, 0.0))
        val json = Stats.getMetrics()("alpha").toJson
        json mustMatch("\"histogram\":\\[")
      }
    }

    "gauges" in {
      val collection = new StatsCollection()

      "report" in {
        collection.addGauge("pi") { java.lang.Math.PI }
        collection.getGauges() mustEqual Map("pi" -> java.lang.Math.PI)
      }

      "setGauge" in {
        collection.setGauge("stew", 11.0)
        collection.getGauge("stew") mustEqual Some(11.0)
      }

      "getGauge" in {
        collection.setGauge("stew", 11.0)
        collection.getGauges() mustEqual Map("stew" -> 11.0)
      }

      "clearGauge" in {
        collection.setGauge("stew", 11.0)
        collection.clearGauge("stew")
        collection.getGauges() mustEqual Map()
      }

      "update" in {
        var potatoes = 100.0
        // gauge that increments every time it's read:
        collection.addGauge("stew") { potatoes += 1.0; potatoes }
        collection.getGauges() mustEqual Map("stew" -> 101.0)
        collection.getGauges() mustEqual Map("stew" -> 102.0)
        collection.getGauges() mustEqual Map("stew" -> 103.0)
      }
    }
  }
}
