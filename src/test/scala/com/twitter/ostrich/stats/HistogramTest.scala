/*
 * Copyright 2010 Twitter, Inc.
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
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.Matchers

@RunWith(classOf[JUnitRunner])
class HistogramTest extends FunSuite with Matchers {

  class Context {
    val histogram = new Histogram()
    val histogram2 = new Histogram()
  }

  test("find the right bucket for various timings") {
    val context = new Context
    import context._

    histogram.add(0)
    assert(histogram.get(true)(0) == 1)
    histogram.add(Int.MaxValue)
    assert(histogram.get(true).last == 1)
    histogram.add(1)
    assert(histogram.get(true)(1) == 1) // offset 2
    histogram.add(2)
    assert(histogram.get(true)(2) == 1) // offset 3
    histogram.add(10)
    histogram.add(11)
    assert(histogram.get(true)(10) == 2) // offset 12
  }

  test("add value buckets.last") {
    val context = new Context
    import context._

    histogram.add(Histogram.buckets.last.toInt)
    assert(histogram.get(true).last == 1)
  }

  test("add value buckets.last+1") {
    val context = new Context
    import context._

    histogram.add(Histogram.buckets.last.toInt + 1)
    assert(histogram.get(true).last == 1)
  }

  test("add value Int.MaxValue") {
    val context = new Context
    import context._

    histogram.add(Int.MaxValue)
    assert(histogram.get(true).last == 1)
  }

  test("add value Int.MinValue") {
    val context = new Context
    import context._

    histogram.add(Int.MinValue)
    assert(histogram.get(true).head == 1)
  }

  test("find histogram cutoffs for various percentages") {
    val context = new Context
    import context._

    for (i <- 0 until 1000) {
      histogram.add(i)
    }

    case class shareABucketWith(n: Int) extends Matcher[Int] {
      def apply(v: Int) = {
        MatchResult(
          Histogram.bucketIndex(n) ==
          Histogram.bucketIndex(v),
          "%d and %d are in the same bucket".format(v, n),
          "%d and %d are not in the same bucket".format(v, n))
      }
    }

    histogram.getPercentile(0.0) should shareABucketWith(0)
    histogram.getPercentile(0.5) should shareABucketWith(500)
    histogram.getPercentile(0.9) should shareABucketWith(900)
    histogram.getPercentile(0.99) should shareABucketWith(998) // 999 is a boundary
    histogram.getPercentile(1.0) should shareABucketWith(1000)
  }


  test("merge") {
    val context = new Context
    import context._

    for (i <- 0 until 50) {
      histogram.add(i * 10)
      histogram2.add(i * 10)
    }
    val origTotal = histogram.count
    histogram.merge(histogram2)
    assert(histogram.count == origTotal + histogram2.count)
    val stats = histogram.get(true)
    val stats2 = histogram2.get(true)
    for (i <- 0 until 50) {
      val bucket = Histogram.bucketIndex(i * 10)
      assert(stats(bucket) == 2 * stats2(bucket))
    }
  }

  test("clone") {
    val context = new Context
    import context._

    for (i <- 0 until 50) {
      histogram.add(i * 10)
    }
    val histClone = histogram.clone()
    assert(histogram.buckets.toList == histClone.buckets.toList)
    assert(histClone.buckets.toList == histogram.buckets.toList)
    assert(histogram.count == histClone.count)
  }

  test("handle a very large timing") {
    val context = new Context
    import context._

    histogram.add(Int.MaxValue)
    assert(histogram.getPercentile(0.0) == Int.MaxValue)
    assert(histogram.getPercentile(0.1) == Int.MaxValue)
    assert(histogram.getPercentile(0.9) == Int.MaxValue)
    assert(histogram.getPercentile(1.0) == Int.MaxValue)
  }

  test("handle an empty histogram") {
    val context = new Context
    import context._

    assert(histogram.getPercentile(0.0) == 0)
    assert(histogram.getPercentile(0.1) == 0)
    assert(histogram.getPercentile(0.9) == 0)
    assert(histogram.getPercentile(1.0) == 0)
  }

  test("track count and sum") {
    val context = new Context
    import context._

    histogram.add(10)
    histogram.add(15)
    histogram.add(20)
    histogram.add(20)
    assert(histogram.count == 4)
    assert(histogram.sum == 65)
  }

  test("getPercentile") {
    val context = new Context
    import context._

    histogram.add(95)
    // bucket covers [91, 99], midpoint is 95
    assert(histogram.getPercentile(0.0) == 95)
    assert(histogram.getPercentile(0.5) == 95)
    assert(histogram.getPercentile(1.0) == 95)
  }

  test("getPercentile with no values") {
    val context = new Context
    import context._

    assert(histogram.getPercentile(0.0) == 0)
    assert(histogram.getPercentile(0.5) == 0)
    assert(histogram.getPercentile(1.0) == 0)
  }

  test("getPercentile with infinity") {
    val context = new Context
    import context._

    histogram.add(Int.MaxValue)
    assert(histogram.getPercentile(0.5) == Int.MaxValue)
  }

  test("minimum") {
    val context = new Context
    import context._

    histogram.add(95)
    assert(histogram.minimum == 95)
  }

  test("minimum with no values") {
    val context = new Context
    import context._

    assert(histogram.minimum == 0)
  }

  test("minimum with infinity") {
    val context = new Context
    import context._

    histogram.add(Int.MaxValue)
    assert(histogram.minimum == Int.MaxValue)
  }

  test("maximum") {
    val context = new Context
    import context._

    histogram.add(95)
    assert(histogram.maximum == 95)
  }

  test("maximum with no values") {
    val context = new Context
    import context._

    assert(histogram.maximum == 0)
  }

  test("maximum with infinity") {
    val context = new Context
    import context._

    histogram.add(Int.MaxValue)
    assert(histogram.maximum == Int.MaxValue)
  }

  test("equals") {
    val context = new Context
    import context._

    assert(histogram == histogram2)
    histogram.add(10)
    assert(histogram !== histogram2)
    histogram2.add(10)
    assert(histogram == histogram2)
    histogram.add(5)
    histogram.add(10)
    histogram2.add(15)
    assert(histogram !== histogram2)
  }

  test("integer overflow shouldn't happen") {
    val context = new Context
    import context._

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

    assert(histogram.getPercentile(0.1) > 0)
  }

  test("Subtracting two histograms must never have negative count") {
    val context = new Context
    import context._

    histogram.add(1)
    histogram2.add(1)
    histogram2.add(10)

    val h = (histogram - histogram2)
    assert(h.count == 0L)
    assert(h.getPercentile(0.9999) == 0)
  }

  test("Substracting two histograms must work") {
    val context = new Context
    import context._

    val n = 10
      (1 to 2*n) foreach { i => histogram.add(i) }
      (1 to n) foreach { i => histogram2.add(i) }
    val histogram3 = new Histogram
      (n+1 to 2*n) foreach { i => histogram3.add(i) }

    assert((histogram - histogram2) == histogram3)
    assert((histogram2 - histogram) == (new Histogram))
  }

}
