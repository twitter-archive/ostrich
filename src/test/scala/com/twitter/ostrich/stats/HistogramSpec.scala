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

package com.twitter.ostrich.stats

import org.specs.SpecificationWithJUnit
import org.specs.matcher.Matcher

class HistogramSpec extends SpecificationWithJUnit {
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
      histogram.add(Int.MaxValue)
      histogram.get(true).last mustEqual 1
      histogram.add(1)
      histogram.get(true)(1) mustEqual 1 // offset 2
      histogram.add(2)
      histogram.get(true)(2) mustEqual 1 // offset 3
      histogram.add(10)
      histogram.add(11)
      histogram.get(true)(10) mustEqual 2 // offset 12
    }

    "add value buckets.last" in {
      histogram.add(Histogram.buckets.last.toInt)
      histogram.get(true).last mustEqual 1
    }

    "add value buckets.last+1" in {
      histogram.add(Histogram.buckets.last.toInt + 1)
      histogram.get(true).last mustEqual 1
    }

    "add value Int.MaxValue" in {
      histogram.add(Int.MaxValue)
      histogram.get(true).last mustEqual 1
    }

    "add value Int.MinValue" in {
      histogram.add(Int.MinValue)
      histogram.get(true).head mustEqual 1
    }

    "find histogram cutoffs for various percentages" in {
      for (i <- 0 until 1000) {
        histogram.add(i)
      }

      case class shareABucketWith(n: Int) extends Matcher[Int]() {
        def apply(v: => Int) = {
          (Histogram.bucketIndex(n) ==
           Histogram.bucketIndex(v),
           "%d and %d are in the same bucket".format(v, n),
           "%d and %d are not in the same bucket".format(v, n))
        }
      }

      histogram.getPercentile(0.0) must shareABucketWith(0)
      histogram.getPercentile(0.5) must shareABucketWith(500)
      histogram.getPercentile(0.9) must shareABucketWith(900)
      histogram.getPercentile(0.99) must shareABucketWith(998) // 999 is a boundary
      histogram.getPercentile(1.0) must shareABucketWith(1000)
    }


    "merge" in {
      for (i <- 0 until 50) {
        histogram.add(i * 10)
        histogram2.add(i * 10)
      }
      val origTotal = histogram.count
      histogram.merge(histogram2)
      histogram.count mustEqual origTotal + histogram2.count
      val stats = histogram.get(true)
      val stats2 = histogram2.get(true)
      for (i <- 0 until 50) {
        val bucket = Histogram.bucketIndex(i * 10)
        stats(bucket) mustEqual 2 * stats2(bucket)
      }
    }

    "clone" in {
      for (i <- 0 until 50) {
        histogram.add(i * 10)
      }
      val histClone = histogram.clone()
      histogram.buckets.toList must containAll(histClone.buckets.toList)
      histClone.buckets.toList must containAll(histogram.buckets.toList)
      histogram.count mustEqual histClone.count
    }

    "handle a very large timing" in {
      histogram.add(Int.MaxValue)
      histogram.getPercentile(0.0) mustEqual Int.MaxValue
      histogram.getPercentile(0.1) mustEqual Int.MaxValue
      histogram.getPercentile(0.9) mustEqual Int.MaxValue
      histogram.getPercentile(1.0) mustEqual Int.MaxValue
    }

    "handle an empty histogram" in {
      histogram.getPercentile(0.0) mustEqual 0
      histogram.getPercentile(0.1) mustEqual 0
      histogram.getPercentile(0.9) mustEqual 0
      histogram.getPercentile(1.0) mustEqual 0
    }

    "track count and sum" in {
      histogram.add(10)
      histogram.add(15)
      histogram.add(20)
      histogram.add(20)
      histogram.count mustEqual 4
      histogram.sum mustEqual 65
    }

    "getPercentile" in {
      histogram.add(95)
      // bucket covers [91, 99], midpoint is 95
      histogram.getPercentile(0.0) mustEqual 95
      histogram.getPercentile(0.5) mustEqual 95
      histogram.getPercentile(1.0) mustEqual 95
    }

    "getPercentile with no values" in {
      histogram.getPercentile(0.0) mustEqual 0
      histogram.getPercentile(0.5) mustEqual 0
      histogram.getPercentile(1.0) mustEqual 0
    }

    "getPercentile with infinity" in {
      histogram.add(Int.MaxValue)
      histogram.getPercentile(0.5) mustEqual Int.MaxValue
    }

    "minimum" in {
      histogram.add(95)
      histogram.minimum mustEqual 95
    }

    "minimum with no values" in {
      histogram.minimum mustEqual 0
    }

    "minimum with infinity" in {
      histogram.add(Int.MaxValue)
      histogram.minimum mustEqual Int.MaxValue
    }

    "maximum" in {
      histogram.add(95)
      histogram.maximum mustEqual 95
    }

    "maximum with no values" in {
      histogram.maximum mustEqual 0
    }

    "maximum with infinity" in {
      histogram.add(Int.MaxValue)
      histogram.maximum mustEqual Int.MaxValue
    }

    "equals" in {
      histogram must beEqual(histogram2)
      histogram.add(10)
      histogram must not(beEqual(histogram2))
      histogram2.add(10)
      histogram must beEqual(histogram2)
      histogram.add(5)
      histogram.add(10)
      histogram2.add(15)
      histogram must not(beEqual(histogram2))
    }

    "integer overflow shouldn't happen" in {
      // This is equivalent of what's commented out below
      val last = histogram.buckets.size - 1
      histogram.buckets(last) = Int.MaxValue
      histogram.buckets(last - 1) = Int.MaxValue
      histogram.count += 2L * Int.MaxValue

      // val n = Int.MaxValue
      // val x = Histogram.buckets.last
      // (1 to n) foreach { _ =>
      //   histogram.add(x)
      //   histogram.add(x - 1)
      // }

      histogram.getPercentile(0.1) must beGreaterThan(0)
    }

    "Subtracting two histograms must never have negative count" in {
      histogram.add(1)
      histogram2.add(1)
      histogram2.add(10)

      val h = (histogram - histogram2)
      h.count mustEqual 0L
      h.getPercentile(0.9999) mustEqual 0
    }

    "Subtracting two histograms must work" in {
      val n = 10
      (1 to 2*n) foreach { i => histogram.add(i) }
      (1 to n) foreach { i => histogram2.add(i) }
      val histogram3 = new Histogram
      (n+1 to 2*n) foreach { i => histogram3.add(i) }

      (histogram - histogram2) mustEqual histogram3
      (histogram2 - histogram) mustEqual (new Histogram)
    }
  }
}
