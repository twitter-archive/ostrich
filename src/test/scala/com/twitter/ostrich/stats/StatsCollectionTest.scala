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

package com.twitter.ostrich.stats

import com.twitter.conversions.string._
import com.twitter.conversions.time._
import com.twitter.logging.{Level, Logger}
import com.twitter.util.{Time, Future}
import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.scalatest.junit.JUnitRunner
import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class StatsCollectionTest extends FunSuite with BeforeAndAfter {

  class Context {
    val collection = new StatsCollection()
  }

  before {
    Logger.get("").setLevel(Level.OFF)
  }

  test("fillInJvmGauges") {
    val context = new Context
    import context._

    val map = new mutable.HashMap[String, Double]
    collection.fillInJvmGauges(map)
    assert(map.keys.toList.contains("jvm_num_cpus"))
    assert(map.keys.toList.contains("jvm_heap_used"))
    assert(map.keys.toList.contains("jvm_start_time"))
    assert(map.keys.toList.contains("jvm_post_gc_used"))
  }

  test("fillInJvmCounters") {
    val context = new Context
    import context._

    val map = new mutable.HashMap[String, Long]
    collection.fillInJvmCounters(map)
    assert(map.keys.toList.contains("jvm_gc_cycles"))
    assert(map.keys.toList.contains("jvm_gc_msec"))
  }

  test("StatsSummary filtering") {
    val summary = StatsSummary(
      Map("apples" -> 10, "oranges" -> 13, "appliances" -> 4, "bad_oranges" -> 1),
      Map(),
      Map(),
      Map()
    )

    assert(summary.filterOut("""app.*""".r).counters == Map("oranges" -> 13, "bad_oranges" -> 1))
    assert(summary.filterOut("""xyz.*""".r).counters == summary.counters)
    assert(summary.filterOut(""".*oranges""".r).counters == Map("apples" -> 10, "appliances" -> 4))
  }

    test("counters") {
      new Context {
        info("basic")
        collection.incr("widgets", 1)
        collection.incr("wodgets", 12)
        collection.incr("wodgets")
        assert(collection.getCounters() == Map("widgets" -> 1, "wodgets" -> 13))
      }

      new Context {
        info("negative")
        collection.incr("widgets", 3)
        collection.incr("widgets", -1)
        assert(collection.getCounters() == Map("widgets" -> 2))
      }

      new Context {
        info("clearCounter")
        collection.getCounter("smellyfeet")
        collection.incr("smellyfeet", 1)
        assert(collection.getCounters() == Map("smellyfeet" -> 1))
        collection.removeCounter("smellyfeet")
        assert(collection.getCounters() == Map())
      }
    }

    test("metrics") {
      new Context {
        info("empty")
        collection.addMetric("test", 0)
        val test = collection.getMetric("test")
        assert(test() == new Distribution(Histogram(0)))
        assert(test() == new Distribution(Histogram(0)))
        // the timings list will be empty here:
        test.clear()
        assert(test() == new Distribution(Histogram()))
      }

      new Context {
        info("basic min/max/average")
        collection.addMetric("test", 1)
        collection.addMetric("test", 2)
        collection.addMetric("test", 3)
        val test = collection.getMetric("test")
        assert(test() == new Distribution(Histogram(1, 2, 3)))
      }

      new Context {
        info("report")
        var x = 0
        collection.time("hundred") { Thread.sleep(10) }
        val timings = collection.getMetrics()
        assert(timings.keys.toList == List("hundred_msec"))
        assert(timings("hundred_msec").count == 1)
        assert(timings("hundred_msec").minimum > 0)
        assert(timings("hundred_msec").maximum > 0)
      }

      new Context {
        info("time future")
        val future = Future({ Thread.sleep(10); 100 })

        assert(collection.timeFutureMillis("latency")(future)() == 100)

        val timings = collection.getMetrics()
        assert(timings("latency_msec").count == 1)
        assert(timings("latency_msec").minimum > 0)
        assert(timings("latency_msec").minimum > 0)
      }

      new Context {
        info("average of 0")
        collection.addMetric("test", 0)
        val test = collection.getMetric("test")
        assert(test() == new Distribution(Histogram(0)))
      }

      new Context {
        info("ignore negative timings")
        collection.addMetric("test", 1)
        collection.addMetric("test", -1)
        collection.addMetric("test", Int.MinValue)
        val test = collection.getMetric("test")
        assert(test() == new Distribution(Histogram(1)))
      }

      new Context {
        info("boundary timing sizes")
        collection.addMetric("test", Int.MaxValue)
        collection.addMetric("test", 5)
        val sum = 5 + Int.MaxValue
        val avg = sum / 2.0
        val test = collection.getMetric("test")
        assert(test() ==
          new Distribution(Histogram(5, Int.MaxValue)))
      }

      new Context {
        info("handle code blocks")
        Time.withCurrentTimeFrozen { time =>
          collection.time("test") {
            time.advance(10.millis)
          }
          val test = collection.getMetric("test_msec")
          assert(test().average >= 10.0)
        }
      }

      new Context {
        info("reset when asked")
        var x = 0
        collection.time("hundred") { for (i <- 0 until 100) x += i }
        assert(collection.getMetric("hundred_msec")().count == 1)
        collection.time("hundred") { for (i <- 0 until 100) x += i }
        assert(collection.getMetric("hundred_msec")().count == 2)
        collection.getMetric("hundred_msec").clear()
        collection.time("hundred") { for (i <- 0 until 100) x += i }
        assert(collection.getMetric("hundred_msec")().count == 1)
      }

      new Context {
        info("add bundle of timings at once")
        val timingStat = new Distribution(Histogram(10, 15, 20))
        collection.addMetric("test", timingStat)
        collection.addMetric("test", 25)
        assert(collection.getMetric("test")() == Distribution(Histogram(10, 15, 20, 25)))
      }

      new Context {
        info("add multiple bundles of timings")
        val timingStat1 = new Distribution(Histogram(15, 25))
        val timingStat2 = new Distribution(Histogram(10, 20, 25))
        collection.addMetric("test", timingStat1)
        collection.addMetric("test", timingStat2)
        assert(collection.getMetric("test")() == Distribution(Histogram(10, 15, 20, 25, 25)))
      }

      new Context {  
        info("timing stats can be added and reflected in Stats.getMetrics")
        Stats.addMetric("foobar", new Distribution(Histogram(10)))
        assert(Stats.getMetrics()("foobar").count == 1)
        Stats.addMetric("foobar", new Distribution(Histogram(20, 30)))
        assert(Stats.getMetrics()("foobar").count == 3)
      }

      new Context {
        info("report text in sorted order")
        Stats.addMetric("alpha", new Distribution(Histogram(0)))
        assert(Stats.getMetrics()("alpha").toString ==
          "(average=0, count=1, maximum=0, minimum=0, " +
          "p50=0, p90=0, p95=0, p99=0, p999=0, p9999=0, sum=0)")
      }
    }

  test("gauges") {

    new Context {
      info("report")
      collection.addGauge("pi") { java.lang.Math.PI }
      assert(collection.getGauges() == Map("pi" -> java.lang.Math.PI))
    }

    new Context {
      info("setGauge")
      collection.setGauge("stew", 11.0)
      assert(collection.getGauge("stew") == Some(11.0))
    }

    new Context {
      info("getGauge")
      collection.setGauge("stew", 11.0)
      assert(collection.getGauges() == Map("stew" -> 11.0))
    }

    new Context {
      info("swallow exceptions")
      collection.addGauge("YIKES") { throw new RuntimeException("YIKES") }
      assert(collection.getGauges() == Map.empty[String, Double])
      assert(collection.getGauge("YIKES") == None)
    }

    new Context {
      info("clearGauge")
      collection.setGauge("stew", 11.0)
      collection.clearGauge("stew")
      assert(collection.getGauges() == Map())
    }

    new Context {
      info("update")
      var potatoes = 100.0
      // gauge that increments every time it's read:
      collection.addGauge("stew") { potatoes += 1.0; potatoes }
      assert(collection.getGauges() == Map("stew" -> 101.0))
      assert(collection.getGauges() == Map("stew" -> 102.0))
      assert(collection.getGauges() == Map("stew" -> 103.0))
    }
  }

}
