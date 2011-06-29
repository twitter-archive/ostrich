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
import com.twitter.util.Time
import org.specs.Specification

object StatsListenerSpec extends Specification {
  "StatsListener" should {
    var collection: StatsCollection = null
    var listener: StatsListener = null
    var listener2: StatsListener = null

    doBefore {
      collection = new StatsCollection()
      listener = new StatsListener(collection)
      listener2 = new StatsListener(collection)
    }

    "reports basic stats" in {
      "counters" in {
        collection.incr("a", 3)
        collection.incr("b", 4)
        listener.getCounters() mustEqual Map("a" -> 3, "b" -> 4)
        collection.incr("a", 2)
        listener.getCounters() mustEqual Map("a" -> 2, "b" -> 0)
      }

      "metrics" in {
        collection.addMetric("beans", 3)
        collection.addMetric("beans", 4)
        collection.getMetrics() mustEqual Map("beans" -> new Distribution(Histogram(3, 4)))
        listener.getMetrics() mustEqual Map("beans" -> new Distribution(Histogram(3, 4)))
        listener2.getMetrics() mustEqual Map("beans" -> new Distribution(Histogram(3, 4)))
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
        listener.getMetrics() mustEqual Map("timing" -> Distribution(Histogram(10, 20)))
        collection.addMetric("timing", 10)
        listener2.getMetrics() mustEqual Map("timing" -> Distribution(Histogram(10, 20, 10)))
        collection.addMetric("timing", 10)
        listener.getMetrics() mustEqual Map("timing" -> Distribution(Histogram(10, 10)))
        listener2.getMetrics() mustEqual Map("timing" -> Distribution(Histogram(10)))

        listener.getMetrics() mustEqual Map("timing" -> Distribution(Histogram()))
        listener2.getMetrics() mustEqual Map("timing" -> Distribution(Histogram()))
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
        listener.getMetrics() mustEqual Map("timing" -> Distribution(Histogram(10, 20)))
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
        Map("beans" -> new Distribution(Histogram(3)),
            "rice" -> new Distribution(Histogram()))
    }

    "latch to the top of a minute" in {
      collection = new StatsCollection()
      val listener = new LatchedStatsListener(collection, 1.minute)

      collection.incr("a", 5)
      listener.getCounters() mustEqual Map()
      listener.nextLatch()
      listener.getCounters() mustEqual Map("a" -> 5)
      collection.incr("a", 5)
      listener.getCounters() mustEqual Map("a" -> 5)
    }

    "named" in {
      collection.incr("a", 5)
      StatsListener("queen", collection).getCounters() mustEqual Map("a" -> 5)
      collection.incr("a", 4)
      StatsListener("queen", collection).getCounters() mustEqual Map("a" -> 4)
      StatsListener("king", collection).getCounters() mustEqual Map("a" -> 9)
    }
  }
}
