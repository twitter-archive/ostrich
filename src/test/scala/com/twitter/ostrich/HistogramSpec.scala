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
    val histogram2 = new Histogram()

    doBefore {
      histogram.clear()
      histogram2.clear()
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

      histogram.getPercentile(0.0) must shareABucketWith(0)
      histogram.getPercentile(0.5) must shareABucketWith(500)
      histogram.getPercentile(0.9) must shareABucketWith(900)
      histogram.getPercentile(0.99) must shareABucketWith(999)
      histogram.getPercentile(1.0) must shareABucketWith(1000)
    }

    "merge" in {
      for (i <- 0 until 50) {
        histogram.add(i * 10)
        histogram2.add(i * 10)
      }
      histogram.merge(histogram2)
      val stats = histogram.get(true)
      val stats2 = histogram2.get(true)
      for (i <- 0 until 50) {
        val bucket = Histogram.binarySearch(i * 10)
        stats(bucket) mustEqual 2 * stats2(bucket)
      }
    }

    "handle a very large timing" in {
      histogram.add(100000)
      histogram.getPercentile(1.0) mustEqual Math.MAX_INT
    }
  }
}
