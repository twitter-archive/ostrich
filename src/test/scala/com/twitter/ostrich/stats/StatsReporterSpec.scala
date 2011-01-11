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

package com.twitter.ostrich
package stats

import com.twitter.conversions.time._
import com.twitter.util.Time
import org.specs.Specification

object StatsReporterSpec extends Specification {
  "StatsReporter" should {
    var collection: StatsCollection = null
    var reporter: StatsReporter = null
    var reporter2: StatsReporter = null

    doBefore {
      collection = new StatsCollection()
      reporter = new StatsReporter(collection)
      reporter2 = new StatsReporter(collection)
    }

    "reports basic stats" in {
      "counters" in {
        collection.incr("a", 3)
        collection.incr("b", 4)
        reporter.getCounters() mustEqual Map("a" -> 3, "b" -> 4)
        collection.incr("a", 2)
        reporter.getCounters() mustEqual Map("a" -> 2, "b" -> 0)
      }

      "metrics" in {
        collection.addMetric("beans", 3)
        collection.addMetric("beans", 4)
        collection.getMetrics() mustEqual Map("beans" -> new Distribution(2, 4, 3, None, 3.5))
        collection.getMetrics() mustEqual Map("beans" -> new Distribution(0, 0, 0, None, 0.0))
      }
    }

    "independently tracks deltas" in {
      collection.incr("a", 3)
      reporter.getCounters() mustEqual Map("a" -> 3)
      collection.incr("a", 5)
      reporter2.getCounters() mustEqual Map("a" -> 8)
      collection.incr("a", 1)
      reporter.getCounters() mustEqual Map("a" -> 6)
    }

    "tracks stats only from the point a reporter was attached, but report all keys" in {
      collection.incr("a", 5)
      collection.incr("b", 5)
      collection.addMetric("beans", 5)
      collection.addMetric("rice", 5)
      val reporter3 = new StatsReporter(collection)
      collection.incr("a", 70)
      collection.incr("a", 300)
      collection.addMetric("beans", 3)
      reporter3.getCounters() mustEqual Map("a" -> 370, "b" -> 0)
      reporter3.getMetrics() mustEqual
        Map("beans" -> new Distribution(1, 3, 3, None, 3.0),
            "rice" -> new Distribution(0, 0, 0, None, 0.0))
    }
  }
}
