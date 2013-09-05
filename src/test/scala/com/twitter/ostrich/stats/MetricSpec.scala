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

import org.specs.SpecificationWithJUnit

class MetricSpec extends SpecificationWithJUnit {
  "Metric" should {
    "min, max, mean" in {
      val metric = new Metric()
      metric.add(10)
      metric.add(20)
      metric() mustEqual Distribution(Histogram(10, 20))
      metric.add(60)
      metric() mustEqual Distribution(Histogram(10, 20, 60))

      metric().histogram.get(false) mustEqual Histogram(10, 20, 60).get(false)
    }

    "add distribution" in {
      val metric = new Metric()
      metric.add(Distribution(Histogram(10, 20)))
      metric.add(60)
      metric() mustEqual Distribution(Histogram(10, 20, 60))
    }

    "clear" in {
      val metric = new Metric()
      metric.add(10)
      metric.add(20)
      metric() mustEqual Distribution(Histogram(10, 20))
      metric() mustEqual Distribution(Histogram(10, 20))
      metric.clear()
      metric() mustEqual Distribution(Histogram())
    }
  }
}
