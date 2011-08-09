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

import scala.annotation.tailrec

object Histogram {
  /*
   * The midpoint of each bucket is +/- 5% from the boundaries.
   *   (0..139).map { |n| (1.10526315 ** n).to_i + 1 }.uniq
   * Bucket i is the range from BUCKET_OFFSETS(i-1) (inclusive) to
   * BUCKET_OFFSETS(i) (exclusive).
   * The last bucket (the "infinity" bucket) is from 1100858 to infinity.
   */
  val BUCKET_OFFSETS =
    Array(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 14, 15, 17, 19, 21, 23, 25, 28,
          31, 34, 37, 41, 45, 50, 55, 61, 67, 74, 82, 91, 100, 111, 122, 135,
          150, 165, 183, 202, 223, 246, 272, 301, 332, 367, 406, 449, 496, 548,
          606, 669, 740, 817, 903, 999, 1104, 1220, 1348, 1490, 1647, 1820, 2011,
          2223, 2457, 2716, 3001, 3317, 3666, 4052, 4479, 4950, 5471, 6047, 6684,
          7387, 8165, 9024, 9974, 11024, 12184, 13467, 14884, 16451, 18182, 20096,
          22212, 24550, 27134, 29990, 33147, 36636, 40492, 44754, 49465, 54672,
          60427, 66787, 73818, 81588, 90176, 99668, 110160, 121755, 134572,
          148737, 164393, 181698, 200824, 221963, 245328, 271152, 299694, 331240,
          366108, 404645, 447240, 494317, 546351, 603861, 667426, 737681, 815331,
          901156, 996014, 1100858)
  val bucketOffsetSize = BUCKET_OFFSETS.size

  def bucketIndex(key: Int): Int = binarySearch(key)

  @tailrec
  private def binarySearch(array: Array[Int], key: Int, low: Int, high: Int): Int = {
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

  def binarySearch(key: Int): Int =
    binarySearch(BUCKET_OFFSETS, key, 0, BUCKET_OFFSETS.length - 1)

  def apply(values: Int*) = {
    val h = new Histogram()
    values.foreach { h.add(_) }
    h
  }
}

class Histogram {
  val numBuckets = Histogram.BUCKET_OFFSETS.length + 1
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
    addToBucket(Histogram.bucketIndex(n))
    sum += n
    count
  }

  def clear() {
    for (i <- 0 until numBuckets) {
      buckets(i) = 0
    }
    count = 0
    sum = 0
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
  def getPercentile(percentile: Double): Int = {
    if (percentile == 0.0)
      return minimum
    var total = 0L
    var index = 0
    while (total < percentile * count) {
      total += buckets(index)
      index += 1
    }
    if (index == 0) {
      0
    } else if (index - 1 >= Histogram.BUCKET_OFFSETS.size) {
      Int.MaxValue
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
      var index = Histogram.BUCKET_OFFSETS.size - 1
      while (index >= 0 && buckets(index) == 0)
        index -= 1
      if (index < 0)
        0
      else
        midpoint(index)
    }
  }

  /**
   * Minimum value within 5%, but:
   *    0 if no values
   *    Int.MaxValue if all values are infinity
   */
  def minimum: Int = {
    if (count == 0) {
      0
    } else {
      var index = 0
      while (index < Histogram.BUCKET_OFFSETS.size && buckets(index) == 0)
        index += 1
      if (index >= Histogram.BUCKET_OFFSETS.size)
        Int.MaxValue
      else
        midpoint(index)
    }
  }

  // Get midpoint of bucket
  protected def midpoint(index: Int): Int = {
    if (index == 0)
      0
    else if (index - 1 >= Histogram.BUCKET_OFFSETS.size)
      Int.MaxValue
    else
      (Histogram.BUCKET_OFFSETS(index - 1) + Histogram.BUCKET_OFFSETS(index) - 1) / 2
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
    rv.count = count - other.count
    rv.sum = sum - other.sum
    for (i <- 0 until numBuckets) {
      rv.buckets(i) = buckets(i) - other.buckets(i)
    }
    rv
  }

  /**
   * Get an immutable snapshot of this histogram.
   */
  def apply(): Distribution = new Distribution(clone())

  override def equals(other: Any) = other match {
    case h: Histogram =>
      h.count == count && h.sum == sum && h.buckets.indices.forall { i => h.buckets(i) == buckets(i) }
    case _ => false
  }

  override def toString = {
    "<Histogram count=" + count + " sum=" + sum +
      buckets.indices.map { i =>
        (if (i < Histogram.BUCKET_OFFSETS.size) Histogram.BUCKET_OFFSETS(i) else "inf") +
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
