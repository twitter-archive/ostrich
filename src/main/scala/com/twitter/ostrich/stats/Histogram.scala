/*
 * Copyright 2010-2011 Twitter, Inc.
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

import java.util.Arrays
import scala.annotation.tailrec

object Histogram {
  /**
   * Given an error (+/-), compute all the bucket values from 1 until we run out of positive
   * 32-bit ints. The error should be in percent, between 0.0 and 1.0.
   *
   * Each bucket's value will be the midpoint of an error range to the edge of the bucket in each
   * direction, so for example, given a 5% error range (the default), the bucket with value N will
   * cover numbers 5% smaller (0.95*N) and 5% larger (1.05*N).
   *
   * For the usual default of 5%, this results in 200 buckets.
   *
   * The last bucket (the "infinity" bucket) ranges up to Int.MaxValue, which we treat as infinity.
   */
  private[this] def makeBucketsFor(error: Double): Array[Long] = {
    def build(factor: Double, n: Double): Stream[Double] = {
      val next = n * factor
      if (next.toInt == Int.MaxValue) Stream.empty else Stream.cons(next, build(factor, next))
    }

    val factor = (1.0 + error) / (1.0 - error)
    (Seq(1L) ++ build(factor, 1.0).map(_.toLong + 1L).distinct.force).toArray
  }

  val buckets = makeBucketsFor(0.05d)

  def bucketIndex(key: Int): Int =
    (Arrays.binarySearch(buckets, key) + 1).abs

  @tailrec
  private[this] def binarySearch(array: Array[Int], key: Int, low: Int, high: Int): Int = {
    if (low > high) {
      low
    } else {
      val mid = (low + high + 1) >> 1
      val midValue = array(mid)
      if (midValue < key) {
        binarySearch(array, key, mid + 1, high)
      } else if (midValue > key) {
        binarySearch(array, key, low, mid - 1)
      } else {
        // exactly equal to this bucket's value. but the value is an exclusive max, so bump it up.
        mid + 1
      }
    }
  }

  def apply(values: Int*) = {
    val h = new Histogram()
    values.foreach { h.add(_) }
    h
  }
}

class Histogram {
  val numBuckets = Histogram.buckets.length + 1
  val buckets = new Array[Long](numBuckets)
  var count = 0L
  var sum = 0L

  /**
   * Adds a value directly to a bucket in a histogram. Can be used for
   * performance reasons when modifying the histogram object from within a
   * synchronized block.
   *
   * @param index the index of the bucket. Should be obtained from a value by
   * calling Histogram.bucketIndex(n) on the value.
   */
  def addToBucket(index: Int) {
    buckets(index) += 1
    count += 1
  }

  def add(n: Int): Long = {
    val index = Histogram.bucketIndex(n)
    synchronized {
      addToBucket(index)
      sum += n
      count
    }
  }

  def clear() {
    synchronized {
      Arrays.fill(buckets, 0)
      count = 0
      sum = 0
    }
  }

  def get(reset: Boolean) = {
    val rv = buckets.toList
    if (reset) {
      clear()
    }
    rv
  }

  /**
   * Percentile within 5%, but:
   *   0 if no values
   *   Int.MaxValue if percentile is out of range
   */
  def getPercentile(percentile: Double): Int = synchronized {
    if (percentile == 0.0) return minimum
    var total = 0L
    var index = 0
    while (index < buckets.size && total < percentile * count) {
      total += buckets(index)
      index += 1
    }
    if (index == 0) {
      0
    } else if (index - 1 == Histogram.buckets.size) {
      maximum
    } else {
      midpoint(index - 1)
    }
  }

  /**
   * Maximum value within 5%, but:
   *    0 if no values
   *    Int.MaxValue if any value is infinity
   */
  def maximum: Int = {
    if (buckets(buckets.size - 1) > 0) {
      // Infinity bucket has a value
      Int.MaxValue
    } else if (count == 0) {
      // No values
      0
    } else {
      var index = Histogram.buckets.size - 1
      while (index >= 0 && buckets(index) == 0) index -= 1
      if (index < 0) 0 else midpoint(index)
    }
  }

  /**
   * Minimum value within error %, but:
   *    0 if no values
   *    Int.MaxValue if all values are infinity
   */
  def minimum: Int = {
    if (count == 0) {
      0
    } else {
      var index = 0
      while (index < Histogram.buckets.size && buckets(index) == 0) index += 1
      if (index >= Histogram.buckets.size) Int.MaxValue else midpoint(index)
    }
  }

  // Get midpoint of bucket
  protected def midpoint(index: Int): Int = {
    if (index == 0) {
      0
    } else if (index - 1 >= Histogram.buckets.size) {
      Int.MaxValue
    } else {
      ((Histogram.buckets(index - 1) + Histogram.buckets(index) - 1) / 2).toInt
    }
  }

  def merge(other: Histogram) {
    if (other.count > 0) {
      for (i <- 0 until numBuckets) {
        buckets(i) += other.buckets(i)
      }
      count += other.count
      sum += other.sum
    }
  }

  def -(other: Histogram): Histogram = {
    val rv = new Histogram()
    rv.sum = math.max(0L, sum - other.sum)
    for (i <- 0 until numBuckets) {
      rv.buckets(i) = math.max(0, buckets(i) - other.buckets(i))
      rv.count += rv.buckets(i)
    }
    rv
  }

  /**
   * Get an immutable snapshot of this histogram.
   */
  def apply(): Distribution = new Distribution(clone())

  override def equals(other: Any) = other match {
    case h: Histogram => {
      h.count == count &&
        h.sum == sum &&
        h.buckets.indices.forall { i => h.buckets(i) == buckets(i) }
    }
    case _ => false
  }

  override def toString = {
    "<Histogram count=" + count + " sum=" + sum +
      buckets.indices.map { i =>
        (if (i < Histogram.buckets.size) Histogram.buckets(i) else "inf") +
        "=" + buckets(i)
      }.mkString(" ", ", ", "") +
      ">"
  }

  override def clone(): Histogram = {
    val histogram = new Histogram
    histogram.merge(this)
    histogram
  }
}
