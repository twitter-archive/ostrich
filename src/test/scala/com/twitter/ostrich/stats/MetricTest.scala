/*
 * Copyright 2011 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.ostrich.stats

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class MetricTest extends FunSuite {

  test("min, max, mean") {
    val metric = new Metric()
    metric.add(10)
    metric.add(20)
    assert(metric() == Distribution(Histogram(10, 20)))
    metric.add(60)
    assert(metric() == Distribution(Histogram(10, 20, 60)))

    assert(metric().histogram.get(false) == Histogram(10, 20, 60).get(false))
  }

  test("add distribution") {
    val metric = new Metric()
    metric.add(Distribution(Histogram(10, 20)))
    metric.add(60)
    assert(metric() == Distribution(Histogram(10, 20, 60)))
  }

  test("clear") {
    val metric = new Metric()
    metric.add(10)
    metric.add(20)
    assert(metric() == Distribution(Histogram(10, 20)))
    assert(metric() == Distribution(Histogram(10, 20)))
    metric.clear()
    assert(metric() == Distribution(Histogram()))
  }

}
