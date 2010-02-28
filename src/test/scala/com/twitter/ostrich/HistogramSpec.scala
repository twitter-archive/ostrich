/*
 * Copyright 2010 Twitter, Inc.
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

import org.specs.Specification
import org.specs.matcher.Matcher


object HistogramSpec extends Specification {
  "Histogram" should {
    val histogram = new Histogram()

    doBefore {
      histogram.clear()
    }

    "find the right bucket for various timings" in {
      histogram.add(0)
      histogram.get(true)(0) mustEqual 1
      histogram.add(99999)
      histogram.get(true).last mustEqual 1
      histogram.add(1)
      histogram.get(true)(1) mustEqual 1
      histogram.add(2)
      histogram.get(true)(2) mustEqual 1
      histogram.add(11)
      histogram.add(12)
      histogram.add(13)
      histogram.get(true)(8) mustEqual 3
    }

    "find histogram cutoffs for various percentages" in {
      for (i <- 0 until 1000) {
        histogram.add(i)
      }

      case class shareABucketWith(n: Int) extends Matcher[Int]() {
        def apply(v: => Int) = {
          (Histogram.binarySearch(n) == Histogram.binarySearch(v),
           "%d and %d are in the same bucket".format(v, n),
           "%d and %d are not in the same bucket".format(v, n))
        }
      }

      histogram.getHistogram(0) must shareABucketWith(0)
      histogram.getHistogram(50) must shareABucketWith(500)
      histogram.getHistogram(90) must shareABucketWith(900)
      histogram.getHistogram(99) must shareABucketWith(999)
      histogram.getHistogram(100) must shareABucketWith(1000)
    }
  }
}
