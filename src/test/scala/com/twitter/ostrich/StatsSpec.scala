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

import scala.collection.immutable
import com.twitter.conversions.string._
import com.twitter.conversions.time._
import com.twitter.logging.{Level, Logger}
import com.twitter.util.Time
import org.specs.Specification

object StatsSpec extends Specification {
  "Stats" should {
    doBefore {
      Logger.get("").setLevel(Level.OFF)
      Stats.clearAll()
    }

    "delta" in {
      Stats.delta(0, 5) mustEqual 5
      Stats.delta(Long.MaxValue - 10, Long.MaxValue) mustEqual 10
      Stats.delta(-4000, -3000) mustEqual 1000
      Stats.delta(Long.MaxValue, Long.MinValue) mustEqual 1
      Stats.delta(Long.MaxValue - 5, Long.MinValue + 3) mustEqual 9
    }

    "jvm stats" in {
      val jvmStats = Stats.getGauges()
      jvmStats.keys.toList must contain("jvm_num_cpus")
      jvmStats.keys.toList must contain("jvm_heap_used")
      jvmStats.keys.toList must contain("jvm_start_time")
    }

    "counters" in {
      Stats.incr("widgets", 1)
      Stats.incr("wodgets", 12)
      Stats.incr("wodgets")
      Stats.getCounters() mustEqual Map("widgets" -> 1, "wodgets" -> 13)
    }

    "metrics" in {
      "empty" in {
        Stats.addMetric("test", 0)
        val test = Stats.getMetric("test")
        test(true) mustEqual new Distribution(1, 0, 0, 0.0)
        // the timings list will be empty here:
        test(true) mustEqual new Distribution(0, 0, 0, 0.0)
      }

      "basic min/max/average" in {
        Stats.addMetric("test", 1)
        Stats.addMetric("test", 2)
        Stats.addMetric("test", 3)
        val test = Stats.getMetric("test")
        test(true) mustEqual new Distribution(3, 3, 1, Some(Histogram(1, 2, 3)), 2.0)
      }

      "report" in {
        var x = 0
        Stats.time("hundred") { for (i <- 0 until 100) x += i }
        val timings = Stats.getMetrics()
        timings.keys.toList mustEqual List("hundred_msec")
        timings("hundred_msec").count mustEqual 1
        timings("hundred_msec").minimum mustEqual timings("hundred_msec").average
        timings("hundred_msec").maximum mustEqual timings("hundred_msec").average
      }

      "average of 0" in {
        Stats.addMetric("test", 0)
        val test = Stats.getMetric("test")
        test(true) mustEqual new Distribution(1, 0, 0, 0.0)
      }

      "ignore negative timings" in {
        Stats.addMetric("test", 1)
        Stats.addMetric("test", -1)
        Stats.addMetric("test", Int.MinValue)
        val test = Stats.getMetric("test")
        test(true) mustEqual new Distribution(1, 1, 1, Some(Histogram(1)), 1.0)
      }

      "boundary timing sizes" in {
        Stats.addMetric("test", Int.MaxValue)
        Stats.addMetric("test", 5)
        val sum = 5.0 + Int.MaxValue
        val avg = sum / 2.0
        val test = Stats.getMetric("test")
        test(true) mustEqual
          new Distribution(2, Int.MaxValue, 5, Some(Histogram(5, Int.MaxValue)), avg)
      }

      "handle code blocks" in {
        Time.withCurrentTimeFrozen { time =>
          Stats.time("test") {
            time.advance(10.millis)
          }
          val test = Stats.getMetric("test_msec")
          test(true).average must be_>=(10.0)
        }
      }

      "reset when asked" in {
        var x = 0
        Stats.time("hundred") { for (i <- 0 until 100) x += i }
        Stats.getMetric("hundred_msec")(false).count mustEqual 1
        Stats.time("hundred") { for (i <- 0 until 100) x += i }
        Stats.getMetric("hundred_msec")(false).count mustEqual 2
        Stats.getMetric("hundred_msec")(true).count mustEqual 2
        Stats.time("hundred") { for (i <- 0 until 100) x += i }
        Stats.getMetric("hundred_msec")(true).count mustEqual 1
      }

      "add bundle of timings at once" in {
        val timingStat = new Distribution(3, 20, 10, Some(Histogram(10, 15, 20)), 15.0)
        Stats.addMetric("test", timingStat)
        Stats.addMetric("test", 25)
        Stats.getMetric("test")(true).count mustEqual 4
        Stats.getMetric("test")(true).average mustEqual 17.5
      }

      "add multiple bundles of timings" in {
        val timingStat1 = new Distribution(2, 25, 15, Some(Histogram(15, 25)), 20.0)
        val timingStat2 = new Distribution(2, 20, 10, Some(Histogram(10, 20)), 15.0)
        Stats.addMetric("test", timingStat1)
        Stats.addMetric("test", timingStat2)
        Stats.getMetric("test")(true).count mustEqual 4
        Stats.getMetric("test")(true).average mustEqual 17.5
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

      "derivative" in {
        collection.incr("results", 100)
        collection.incr("queries", 25)
        collection.addDerivativeGauge("results_per_query", collection.getCounter("results"),
                                      collection.getCounter("queries"))
        collection.getGauges() mustEqual Map("results_per_query" -> 4.0)
        collection.getGauges() mustEqual Map("results_per_query" -> 0.0)
        collection.incr("results", 10)
        collection.incr("queries", 5)
        collection.getGauges() mustEqual Map("results_per_query" -> 2.0)
        collection.getGauges() mustEqual Map("results_per_query" -> 0.0)
      }
    }

/*
    "fork" in {
      "newly created stats are available in the fork and in the global Stats" in {
        val collection = Stats.fork()
        Stats.incr("widgets", 5)
        collection.getCounterStats(false) mustEqual Map("widgets" -> 5)
        Stats.getCounterStats(true) mustEqual Map("widgets" -> 5)
      }

      "modifications to forks are available only in the fork" in {
        val collection = Stats.fork()
        Stats.incr("widgets", 5)
        collection.getCounterStats(false) mustEqual Map("widgets" -> 5)
        Stats.getCounterStats(true) mustEqual Map("widgets" -> 5)

        Stats.incr("widgets", 5)
        collection.getCounterStats(false) mustEqual Map("widgets" -> 10)
        Stats.getCounterStats(true) mustEqual Map("widgets" -> 5)
      }

      "keeps the name of older generated stats with zeroed out values" in {
        Stats.incr("wodgets", 1)
        val collection = Stats.fork()

        Stats.getCounterStats(false) must havePair("wodgets" -> 1)
        collection.getCounterStats(false) must havePair("wodgets" -> 0)
      }
    }
    */
  }
}
