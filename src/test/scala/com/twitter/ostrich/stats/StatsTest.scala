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

import com.twitter.conversions.time._
import com.twitter.util.Time
import org.specs.SpecificationWithJUnit
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite

@RunWith(classOf[JUnitRunner])
class StatsTest extends FunSuite {

  test("delta") {
    assert(Stats.delta(0, 5) === 5)
    assert(Stats.delta(Long.MaxValue - 10, Long.MaxValue) === 10)
    assert(Stats.delta(-4000, -3000) === 1000)
    assert(Stats.delta(Long.MaxValue, Long.MinValue) === 1)
    assert(Stats.delta(Long.MaxValue - 5, Long.MinValue + 3) === 9)
  }

}
