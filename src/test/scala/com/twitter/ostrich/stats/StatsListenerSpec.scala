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
import org.specs.SpecificationWithJUnit
import org.specs.util.Duration

class StatsListenerSpec extends SpecificationWithJUnit {
  "StatsListener object" should {
    var collection: StatsCollection = null

    doBefore {
      collection = new StatsCollection()
      StatsListener.clearAll()
    }

    "track latched listeners" in {
      StatsListener.listeners.size() mustEqual 0
      val listener = StatsListener(1.minute, collection)
      val listener2 = StatsListener(1.minute, collection)
      listener must be(listener2)
      StatsListener.listeners.size() mustEqual 1
      StatsListener(500.millis, collection) mustNot be(listener)
      StatsListener.listeners.size() mustEqual 2
      val key = ("period:%d".format(1.minute.inMillis), collection)
      StatsListener.listeners.containsKey(key) must beTrue
      StatsListener.listeners.get(key) mustEqual listener
    }

    "tracks named listeners" in {
      val monkeyListener = StatsListener("monkey", collection)
      StatsListener("donkey", collection) mustNot be(monkeyListener)
      StatsListener("monkey", collection) must be(monkeyListener)
    }
  }

  "StatsListener instance" should {
    var collection: StatsCollection = null
    var listener: StatsListener = null
    var listener2: StatsListener = null

    doBefore {
      collection = new StatsCollection()
      listener = new StatsListener(collection)
      listener2 = new StatsListener(collection)
      StatsListener.clearAll()
    }


    "reports basic stats" in {
      "counters" in {
        collection.incr("b", 4)
        collection.incr("a", 3)

        listener.getCounters() mustEqual Map("a" -> 3, "b" -> 4)
        collection.incr("a", 2)
        listener.getCounters() mustEqual Map("a" -> 2, "b" -> 0)
      }

      "metrics" in {
        collection.addMetric("beans", 3)
        collection.addMetric("beans", 4)
        collection.getMetrics() mustEqual Map("beans" -> Distribution(Histogram(3, 4)))
        listener.getMetrics() mustEqual Map("beans" -> Histogram(3, 4))
        listener2.getMetrics() mustEqual Map("beans" -> Histogram(3, 4))
      }
    }

    "independently tracks deltas" in {
      "counters" in {
        collection.incr("a", 3)
        listener.getCounters() mustEqual Map("a" -> 3)
        collection.incr("a", 5)
        listener2.getCounters() mustEqual Map("a" -> 8)
        collection.incr("a", 1)
        listener.getCounters() mustEqual Map("a" -> 6)
      }

      "metrics" in {
        collection.addMetric("timing", 10)
        collection.addMetric("timing", 20)
        listener.getMetrics() mustEqual Map("timing" -> Histogram(10, 20))
        collection.addMetric("timing", 10)
        listener2.getMetrics() mustEqual Map("timing" -> Histogram(10, 20, 10))
        collection.addMetric("timing", 10)
        listener.getMetrics() mustEqual Map("timing" -> Histogram(10, 10))
        listener2.getMetrics() mustEqual Map("timing" -> Histogram(10))

        listener.getMetrics() mustEqual Map("timing" -> Histogram())
        listener2.getMetrics() mustEqual Map("timing" -> Histogram())
      }
    }

    "master stats always increase, even with listeners connected" in {
      "counters" in {
        collection.incr("a", 3)
        listener.getCounters() mustEqual Map("a" -> 3)
        collection.incr("a", 5)
        listener.getCounters() mustEqual Map("a" -> 5)

        collection.getCounters() mustEqual Map("a" -> 8)
      }

      "metrics" in {
        collection.addMetric("timing", 10)
        collection.addMetric("timing", 20)
        listener.getMetrics() mustEqual Map("timing" -> Histogram(10, 20))
        collection.addMetric("timing", 10)

        collection.getMetrics() mustEqual Map("timing" -> Distribution(Histogram(10, 20, 10)))
      }
    }

    "tracks stats only from the point a listener was attached, but report all keys" in {
      collection.incr("a", 5)
      collection.incr("b", 5)
      collection.addMetric("beans", 5)
      collection.addMetric("rice", 5)
      val listener3 = new StatsListener(collection)
      collection.incr("a", 70)
      collection.incr("a", 300)
      collection.addMetric("beans", 3)
      listener3.getCounters() mustEqual Map("a" -> 370, "b" -> 0)
      listener3.getMetrics() mustEqual
        Map("beans" -> Histogram(3),
            "rice" -> Histogram())
    }
  }

  "LatchedStatsListener instance" should {
    "latch to the top of a period" in {
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

      listener.getCounters() mustEqual Map()
      listener.getGauges() mustEqual Map()
      listener.getLabels() mustEqual Map()
      listener.getMetrics() mustEqual Map()

      listener.nextLatch()

      listener.getCounters() mustEqual Map("counter" -> 5)
      listener.getGauges() mustEqual Map("gauge" -> 0)
      listener.getLabels() mustEqual Map("label" -> "HIMYNAMEISBRAK")
      listener.getMetrics() mustEqual Map("metric" -> Histogram(1, 2))

      collection.incr("counter", 3)
      synchronized { gauge = 37 }
      collection.setLabel("label", "EEPEEPIAMAMONKEY")
      collection.addMetric("metric", Distribution(Histogram(3, 4, 5)))

      listener.getCounters() mustEqual Map("counter" -> 5)
      listener.getGauges() mustEqual Map("gauge" -> 0)
      listener.getLabels() mustEqual Map("label" -> "HIMYNAMEISBRAK")
      listener.getMetrics() mustEqual Map("metric" -> Histogram(1, 2))

      listener.nextLatch()

      listener.getCounters() mustEqual Map("counter" -> 3)
      listener.getGauges() mustEqual Map("gauge" -> 37)
      listener.getLabels() mustEqual Map("label" -> "EEPEEPIAMAMONKEY")
      listener.getMetrics() mustEqual Map("metric" -> Histogram(3, 4, 5))
    }
  }
}
