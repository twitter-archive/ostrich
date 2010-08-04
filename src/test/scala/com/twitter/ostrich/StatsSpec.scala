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

import scala.collection.immutable
import com.twitter.xrayspecs.Time
import com.twitter.xrayspecs.TimeConversions._
import net.lag.extensions._
import org.specs._


object StatsSpec extends Specification {
  "Stats" should {
    doBefore {
      Stats.clearAll()
    }

    "jvm stats" in {
      val jvmStats = Stats.getJvmStats()
      jvmStats.keys.toList must contain("num_cpus")
      jvmStats.keys.toList must contain("heap_used")
      jvmStats.keys.toList must contain("start_time")
    }

    "counters" in {
      Stats.incr("widgets", 1)
      Stats.incr("wodgets", 12)
      Stats.incr("wodgets")
      Stats.getCounterStats() mustEqual Map("widgets" -> 1, "wodgets" -> 13)
    }

    "timings" in {
      "empty" in {
        Stats.addTiming("test", 0)
        val test = Stats.getTiming("test")
        test.get(true) mustEqual new TimingStat(1, 0, 0)
        // the timings list will be empty here:
        test.get(true) mustEqual new TimingStat(0, 0, 0)
      }

      "basic min/max/average" in {
        Stats.addTiming("test", 1)
        Stats.addTiming("test", 2)
        Stats.addTiming("test", 3)
        val test = Stats.getTiming("test")
        test.get(true) mustEqual new TimingStat(3, 3, 1, Some(Histogram(1, 2, 3)), 2.0, 2.0)
      }

      "report" in {
        var x = 0
        Stats.time("hundred") { for (i <- 0 until 100) x += i }
        val timings = Stats.getTimingStats(false)
        timings.keys.toList mustEqual List("hundred")
        timings("hundred").count mustEqual 1
        timings("hundred").minimum mustEqual timings("hundred").average
        timings("hundred").maximum mustEqual timings("hundred").average
      }

      "average of 0" in {
        Stats.addTiming("test", 0)
        val test = Stats.getTiming("test")
        test.get(true) mustEqual new TimingStat(1, 0, 0)
      }

      "ignore negative timings" in {
        Stats.addTiming("test", 1)
        Stats.addTiming("test", -1)
        Stats.addTiming("test", Math.MIN_INT)
        val test = Stats.getTiming("test")
        test.get(true) mustEqual new TimingStat(1, 1, 1, Some(Histogram(1)), 1.0, 0.0)
      }

      "boundary timing sizes" in {
        Stats.addTiming("test", Math.MAX_INT)
        Stats.addTiming("test", 5)
        val sum = 5.0 + Math.MAX_INT
        val avg = sum / 2.0
        val sumsq = 5.0 * 5.0 + Math.MAX_INT.toDouble * Math.MAX_INT.toDouble
        val partial = sumsq - sum * avg
        val test = Stats.getTiming("test")
        test.get(true) mustEqual
          new TimingStat(2, Math.MAX_INT, 5, Some(Histogram(5, Math.MAX_INT)), avg, partial)
      }

      "handle code blocks" in {
        Stats.time("test") {
          Time.advance(10.millis)
        }
        val test = Stats.getTiming("test")
        test.get(true).average must be_>=(10)
      }

      "reset when asked" in {
        var x = 0
        Stats.time("hundred") { for (i <- 0 until 100) x += i }
        Stats.getTimingStats(false)("hundred").count mustEqual 1
        Stats.time("hundred") { for (i <- 0 until 100) x += i }
        Stats.getTimingStats(false)("hundred").count mustEqual 2
        Stats.getTimingStats(true)("hundred").count mustEqual 2
        Stats.time("hundred") { for (i <- 0 until 100) x += i }
        Stats.getTimingStats(false)("hundred").count mustEqual 1
      }

      "add bundle of timings at once" in {
        val timingStat = new TimingStat(3, 20, 10, Some(Histogram(10, 15, 20)), 15.0, 50.0)
        Stats.addTiming("test", timingStat)
        Stats.addTiming("test", 25)
        Stats.getTimingStats(false)("test").count mustEqual 4
        Stats.getTimingStats(false)("test").average mustEqual 17
        Stats.getTimingStats(false)("test").standardDeviation.toInt mustEqual 6
      }

      "add multiple bundles of timings" in {
        val timingStat1 = new TimingStat(2, 25, 15, Some(Histogram(15, 25)), 20.0, 50.0)
        val timingStat2 = new TimingStat(2, 20, 10, Some(Histogram(10, 20)), 15.0, 50.0)
        Stats.addTiming("test", timingStat1)
        Stats.addTiming("test", timingStat2)
        Stats.getTimingStats(false)("test").count mustEqual 4
        Stats.getTimingStats(false)("test").average mustEqual 17
        Stats.getTimingStats(false)("test").standardDeviation.toInt mustEqual 6
      }

      "timing stats can be added and reflected in Stats.getTimingStats" in {
        var x = 0
        Stats.time("hundred") { for (i <- 0 until 100) x += 1 }
        Stats.getTimingStats(false).size mustEqual 1

        Stats.addTiming("foobar", new TimingStat(1, 0, 0))
        Stats.getTimingStats(false).size mustEqual 2
        Stats.getTimingStats(true)("foobar").count mustEqual 1
        Stats.addTiming("foobar", new TimingStat(3, 0, 0))
        Stats.getTimingStats(false)("foobar").count mustEqual 3
      }

      "timing stats can be pulled from a passive external source" in {
        Stats.registerTimingSource { () => Map("made_up" -> new TimingStat(1, 1, 1, None, 1.0, 0.0)) }
        Stats.getTimingStats(false).size mustEqual 1
        Stats.getTimingStats(false)("made_up").count mustEqual 1
        Stats.getTimingStats(false)("made_up").average mustEqual 1
      }

      "report text in sorted order" in {
        Stats.addTiming("alpha", new TimingStat(1, 0, 0))
        Stats.getTimingStats(false)("alpha").toString mustEqual
          "(average=0, count=1, maximum=0, minimum=0, " +
          "p25=0, p50=0, p75=0, p90=0, p99=0, p999=0, p9999=0, " +
          "standard_deviation=0)"
      }

      "json contains histogram buckets" in {
        Stats.addTiming("alpha", new TimingStat(1, 0, 0))
        val json = Stats.getTimingStats(false)("alpha").toJson
        json mustMatch("\"histogram\":\\[")
      }
    }

    "gauges" in {
      "report" in {
        Stats.makeGauge("pi") { java.lang.Math.PI }
        Stats.getGaugeStats(false) mustEqual Map("pi" -> java.lang.Math.PI)
      }
      
      "setGauge" in {
        Stats.setGauge("stew", 11.0)
        Stats.getGaugeStats(true) mustEqual Map("stew" -> 11.0)
      }
      
      "clearGauge" in {
        Stats.setGauge("stew", 11.0)
        Stats.clearGauge("stew")
        Stats.getGaugeStats(true) mustEqual Map()
      }

      "update" in {
        var potatoes = 100.0
        // gauge that increments every time it's read:
        Stats.makeGauge("stew") { potatoes += 1.0; potatoes }
        Stats.getGaugeStats(true) mustEqual Map("stew" -> 101.0)
        Stats.getGaugeStats(true) mustEqual Map("stew" -> 102.0)
        Stats.getGaugeStats(true) mustEqual Map("stew" -> 103.0)
      }

      "derivative" in {
        Stats.incr("results", 100)
        Stats.incr("queries", 25)
        Stats.makeDerivativeGauge("results_per_query", Stats.getCounter("results"),
                                  Stats.getCounter("queries"))
        Stats.getGaugeStats(true) mustEqual Map("results_per_query" -> 4.0)
        Stats.getGaugeStats(true) mustEqual Map("results_per_query" -> 0.0)
        Stats.incr("results", 10)
        Stats.incr("queries", 5)
        Stats.getGaugeStats(false) mustEqual Map("results_per_query" -> 2.0)
        Stats.getGaugeStats(false) mustEqual Map("results_per_query" -> 2.0)
      }
    }

    "report to JMX" in {
      Stats.incr("widgets", 1)
      Stats.time("nothing") { 2 * 2 }

      val mbean = new StatsMBean
      val names = mbean.getMBeanInfo().getAttributes().toList.map { _.getName() }
      names mustEqual List("counter_widgets", "timing_nothing")
    }

    "fork" in {
      val collection = Stats.fork()
      Stats.incr("widgets", 5)
      collection.getCounterStats(false) mustEqual Map("widgets" -> 5)
      Stats.getCounterStats(true) mustEqual Map("widgets" -> 5)
      Stats.incr("widgets", 5)
      collection.getCounterStats(false) mustEqual Map("widgets" -> 10)
      Stats.getCounterStats(true) mustEqual Map("widgets" -> 5)
    }
  }
}
