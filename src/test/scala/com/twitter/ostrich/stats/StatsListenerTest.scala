/*
 * Copyright 2011 Twitter, Inc.
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

import com.twitter.conversions.time._
import com.twitter.ostrich.admin.PeriodicBackgroundProcess
import com.twitter.util.Future
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class StatsListenerTest extends FunSuite {

  class StatsListenerObjectContext {
    var collection: StatsCollection = new StatsCollection()
    StatsListener.clearAll()
  }

  test("tracks latched listeners") {
    val context = new StatsListenerObjectContext
    import context._

    assert(StatsListener.listeners.size() === 0)
    val listener = StatsListener(1.minute, collection)
    val listener2 = StatsListener(1.minute, collection)
    assert(listener === listener2)
    assert(StatsListener.listeners.size() === 1)
    assert(StatsListener(500.millis, collection) !== listener)
    assert(StatsListener.listeners.size() === 2)
    val key = ("period:%d".format(1.minute.inMillis), collection)
    assert(StatsListener.listeners.containsKey(key))
    assert(StatsListener.listeners.get(key) === listener)
  }

  test("tracks named listeners") {
    val context = new StatsListenerObjectContext
    import context._

    val monkeyListener = StatsListener("monkey", collection)
    assert(StatsListener("donkey", collection) !== monkeyListener)
    assert(StatsListener("monkey", collection) === monkeyListener)
  }

  class StatsListenerInstanceContext {
    var collection: StatsCollection = new StatsCollection()
    var listener: StatsListener = new StatsListener(collection)
    var listener2: StatsListener = new StatsListener(collection)
    StatsListener.clearAll()
  }

  test("reports basic stats") {
    val context = new StatsListenerInstanceContext
    import context._

    info("counters")
    collection.incr("b", 4)
    collection.incr("a", 3)

    assert(listener.getCounters() === Map("a" -> 3, "b" -> 4))
    collection.incr("a", 2)
    assert(listener.getCounters() === Map("a" -> 2, "b" -> 0))

    info("metrics")
    collection.addMetric("beans", 3)
    collection.addMetric("beans", 4)
    assert(collection.getMetrics() === Map("beans" -> Distribution(Histogram(3, 4))))
    assert(listener.getMetrics() === Map("beans" -> Histogram(3, 4)))
    assert(listener2.getMetrics() === Map("beans" -> Histogram(3, 4)))
  }

  test("independently tracks deltas") {
    val context = new StatsListenerInstanceContext
    import context._

    info("counters")
    collection.incr("a", 3)
    assert(listener.getCounters() === Map("a" -> 3))
    collection.incr("a", 5)
    assert(listener2.getCounters() === Map("a" -> 8))
    collection.incr("a", 1)
    assert(listener.getCounters() === Map("a" -> 6))

    info("metrics")
    collection.addMetric("timing", 10)
    collection.addMetric("timing", 20)
    assert(listener.getMetrics() === Map("timing" -> Histogram(10, 20)))
    collection.addMetric("timing", 10)
    assert(listener2.getMetrics() === Map("timing" -> Histogram(10, 20, 10)))
    collection.addMetric("timing", 10)
    assert(listener.getMetrics() === Map("timing" -> Histogram(10, 10)))
    assert(listener2.getMetrics() === Map("timing" -> Histogram(10)))

    assert(listener.getMetrics() === Map("timing" -> Histogram()))
    assert(listener2.getMetrics() === Map("timing" -> Histogram()))
  }

  test("master stats always increase, even with listeners connected") {
    val context = new StatsListenerInstanceContext
    import context._

    info("counters")
    collection.incr("a", 3)
    assert(listener.getCounters() === Map("a" -> 3))
    collection.incr("a", 5)
    assert(listener.getCounters() === Map("a" -> 5))

    assert(collection.getCounters() === Map("a" -> 8))

    info("metrics")
    collection.addMetric("timing", 10)
    collection.addMetric("timing", 20)
    assert(listener.getMetrics() === Map("timing" -> Histogram(10, 20)))
    collection.addMetric("timing", 10)

    assert(collection.getMetrics() === Map("timing" -> Distribution(Histogram(10, 20, 10))))
  }


  test("tracks stats only from the point a listener was attached, but report all keys") {
    val context = new StatsListenerInstanceContext
    import context._

    collection.incr("a", 5)
    collection.incr("b", 5)
    collection.addMetric("beans", 5)
    collection.addMetric("rice", 5)
    val listener3 = new StatsListener(collection)
    collection.incr("a", 70)
    collection.incr("a", 300)
    collection.addMetric("beans", 3)
    assert(listener3.getCounters() === Map("a" -> 370, "b" -> 0))
    assert(listener3.getMetrics() ===
    Map("beans" -> Histogram(3),
      "rice" -> Histogram()))
  }

  test("latch to the top of a period") {
    val collection = new StatsCollection()
    val listener = new LatchedStatsListener(collection, 1.second) {
      override lazy val service = new PeriodicBackgroundProcess("", 1.second) {
        def periodic() { }
      }
    }

    var gauge = 0
    collection.incr("counter", 5)
    collection.addGauge("gauge") { synchronized { gauge } }
    collection.setLabel("label", "HIMYNAMEISBRAK")
    collection.addMetric("metric", Distribution(Histogram(1, 2)))

    assert(listener.getCounters() === Map())
    assert(listener.getGauges() === Map())
    assert(listener.getLabels() === Map())
    assert(listener.getMetrics() === Map())

    listener.nextLatch()

    assert(listener.getCounters() === Map("counter" -> 5))
    assert(listener.getGauges() === Map("gauge" -> 0))
    assert(listener.getLabels() === Map("label" -> "HIMYNAMEISBRAK"))
    assert(listener.getMetrics() === Map("metric" -> Histogram(1, 2)))

    collection.incr("counter", 3)
    synchronized { gauge = 37 }
    collection.setLabel("label", "EEPEEPIAMAMONKEY")
    collection.addMetric("metric", Distribution(Histogram(3, 4, 5)))

    assert(listener.getCounters() === Map("counter" -> 5))
    assert(listener.getGauges() === Map("gauge" -> 0))
    assert(listener.getLabels() === Map("label" -> "HIMYNAMEISBRAK"))
    assert(listener.getMetrics() === Map("metric" -> Histogram(1, 2)))

    listener.nextLatch()

    assert(listener.getCounters() === Map("counter" -> 3))
    assert(listener.getGauges() === Map("gauge" -> 37))
    assert(listener.getLabels() === Map("label" -> "EEPEEPIAMAMONKEY"))
    assert(listener.getMetrics() === Map("metric" -> Histogram(3, 4, 5)))
  }

}
