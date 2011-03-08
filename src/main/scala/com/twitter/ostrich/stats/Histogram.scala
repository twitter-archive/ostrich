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

import scala.annotation.tailrec

object Histogram {
  // (0..53).map { |n| (1.3 ** n).to_i + 1 }.uniq
  val BUCKET_OFFSETS =
    Array(1, 2, 3, 4, 5, 7, 9, 11, 14, 18, 24, 31, 40, 52, 67, 87, 113, 147, 191, 248,
          322, 418, 543, 706, 918, 1193, 1551, 2016, 2620, 3406, 4428, 5757, 7483,
          9728, 12647, 16441, 21373, 27784, 36119, 46955, 61041, 79354, 103160, 134107,
          174339, 226641, 294633, 383023, 497930, 647308, 841501, 1093951)
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
  val buckets = new Array[Int](numBuckets)
  var total = 0

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
    total += 1
  }

  def add(n: Int) {
    addToBucket(Histogram.bucketIndex(n))
  }

  def clear() {
    for (i <- 0 until numBuckets) {
      buckets(i) = 0
    }
    total = 0
  }

  def get(reset: Boolean) = {
    val rv = buckets.toList
    if (reset) {
      clear()
    }
    rv
  }

  def getPercentile(percentile: Double): Int = {
    var sum = 0
    var index = 0
    while (sum < percentile * total) {
      sum += buckets(index)
      index += 1
    }
    if (index == 0) {
      0
    } else if (index - 1 >= Histogram.BUCKET_OFFSETS.size) {
      Int.MaxValue
    } else {
      Histogram.BUCKET_OFFSETS(index - 1) - 1
    }
  }

  def merge(other: Histogram) {
    for (i <- 0 until numBuckets) {
      buckets(i) += other.buckets(i)
    }
    total += other.total
  }

  override def clone(): Histogram = {
    val histogram = new Histogram
    histogram.merge(this)
    histogram
  }
}
