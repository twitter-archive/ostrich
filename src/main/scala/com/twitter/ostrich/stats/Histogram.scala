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

import java.lang.{Math => JLMath}
import java.util.Arrays
import scala.annotation.tailrec
import com.twitter.jsr166e.LongAdder

object Histogram {
  /**
   * Given an error (+/-), compute all the bucket values from 1 until we run out of positive
   * 32-bit ints. The error should be in percent, between 0.0 and 1.0.
   *
   * Each bucket's value will be the midpoint of an error range to the edge of the bucket in each
   * direction, so for example, given a 5% error range (the default), the bucket with value N will
   * cover numbers 5% smaller (0.95*N) and 5% larger (1.05*N).
   *
   * For the usual default of 5%, this results in 200 _buckets.
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
    JLMath.abs(Arrays.binarySearch(buckets, key) + 1)

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
  private final val num_buckets = Histogram.buckets.length + 1
  private[stats] final val _buckets = Array.fill[LongAdder](num_buckets)(new LongAdder)
  private[stats] final val _count = new LongAdder
  private final val _sum = new LongAdder

  def count : Long = _count.longValue()
  def sum : Long = _sum.longValue()
  def buckets: Array[Long] = _buckets.map(_.longValue())

  /**
   * Adds a value directly to a bucket in a histogram. Can be used for
   * performance reasons when modifying the histogram object from within a
   * synchronized block.
   *
   * @param index the index of the bucket. Should be obtained from a value by
   * calling Histogram.bucketIndex(n) on the value.
   */
  def addToBucket(index: Int) {
    _buckets(index).increment()
    _count.increment()
  }

  def add(n: Int): Unit = {
    val index = Histogram.bucketIndex(n)
    addToBucket(index)
    _sum.add(n)
  }

  def clear() {
    synchronized {
      _buckets.foreach(_.reset())
      _count.reset()
      _sum.reset()
    }
  }

  def get(reset: Boolean) : List[Long] = {
    val rv = _buckets.toList.map(_.longValue())
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
    val currentCount = _count.longValue()
    while (index < _buckets.size && total < percentile * currentCount) {
      total += _buckets(index).longValue()
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
    if (_buckets(_buckets.size - 1).longValue() > 0) {
      // Infinity bucket has a value
      Int.MaxValue
    } else if (_count.longValue() == 0) {
      // No values
      0
    } else {
      var index = Histogram.buckets.size - 1
      while (index >= 0 && _buckets(index).longValue() == 0) index -= 1
      if (index < 0) 0 else midpoint(index)
    }
  }

  /**
   * Minimum value within error %, but:
   *    0 if no values
   *    Int.MaxValue if all values are infinity
   */
  def minimum: Int = {
    if (_count.longValue() == 0) {
      0
    } else {
      var index = 0
      while (index < Histogram.buckets.size && _buckets(index).longValue() == 0) index += 1
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
    if (other._count.longValue() > 0) {
      for (i <- 0 until num_buckets) {
        _buckets(i).add(other._buckets(i).longValue())
      }
      _count.add(other._count.longValue())
      _sum.add(other._sum.longValue())
    }
  }

  def -(other: Histogram): Histogram = {
    val rv = new Histogram()
    rv._sum.reset()
    rv._sum.add(math.max(0L, _sum.longValue() - other._sum.longValue()))
    for (i <- 0 until num_buckets) {
      rv._buckets(i) = {
        val a = new LongAdder
        a.add(math.max(0, _buckets(i).longValue() - other._buckets(i).longValue()))
        a
      }
      rv._count.add(rv._buckets(i).longValue())
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
    "<Histogram count=" + _count + " sum=" + _sum +
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
