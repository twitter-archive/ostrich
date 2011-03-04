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

import org.specs.Specification

object MetricSpec extends Specification {
  "Metric" should {
    "min, max, mean" in {
      val metric = new Metric()
      metric.add(10)
      metric.add(20)
      metric.apply(false) mustEqual Distribution(2, 20, 10, None, 15.0)
      metric.add(60)
      metric.apply(false) mustEqual Distribution(3, 60, 10, None, 30.0)

      metric.apply(false).histogram.get.get(false) mustEqual Histogram(10, 20, 60).get(false)
    }

    "add distribution" in {
      val metric = new Metric()
      metric.add(Distribution(2, 20, 10, None, 15.0))
      metric.add(60)
      metric.apply(false) mustEqual Distribution(3, 60, 10, None, 30.0)
    }

    "clear" in {
      val metric = new Metric()
      metric.add(10)
      metric.add(20)
      metric.apply(false) mustEqual Distribution(2, 20, 10, None, 15.0)
      metric.apply(true) mustEqual Distribution(2, 20, 10, None, 15.0)
      metric.apply(true) mustEqual Distribution(0, 0, 0, None, 0.0)
    }

    "fanout" in {
      val fanout = new FanoutMetric()
      fanout.add(10)

      val metric = new Metric()
      fanout.addFanout(metric)
      fanout.add(20)

      fanout.apply(false) mustEqual Distribution(2, 20, 10, None, 15.0)
      metric.apply(false) mustEqual Distribution(1, 20, 20, None, 20.0)

      fanout.apply(true) mustEqual Distribution(2, 20, 10, None, 15.0)

      fanout.apply(true) mustEqual Distribution(0, 0, 0, None, 0.0)
      metric.apply(true) mustEqual Distribution(0, 0, 0, None, 0.0)
    }
  }
}
