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

package com.twitter.ostrich

import scala.collection.Map
import scala.collection.immutable
import scala.util.Sorting
import com.twitter.json.{Json, JsonSerializable}


/**
 * A pre-calculated timing. If you have timing stats from an external source but
 * still want to report them via the Stats interface, use this.
 */
class TimingStat(_count: Int, _maximum: Int, _minimum: Int, _sum: Long, _sumSquares: Long,
                 _histogram: Option[Histogram]) extends JsonSerializable {
  def count = _count
  def minimum = if (_count > 0) _minimum else 0
  def maximum = if (_count > 0) _maximum else 0
  def average = if (_count > 0) (_sum / _count).toInt else 0
  def variance = if (_count > 1) ((_sumSquares - _sum * average) / (_count - 1)).toInt else 0
  def standardDeviation = Math.sqrt(variance)
  def sum = _sum
  def sumSquares = _sumSquares
  def histogram = _histogram

  def this(_count: Int, _maximum: Int, _minimum: Int, _sum: Long, _sumSquares: Long) =
    this(_count, _maximum, _minimum, _sum, _sumSquares, None)

  // FIXME: this ought to be generally available
  class SortableSeq[T <% Ordered[T]](seq: Iterator[T]) {
    def sorted = Sorting.stableSort(seq.toList)
  }
  implicit def sortableSeq[T <% Ordered[T]](seq: Iterator[T]) = new SortableSeq(seq)

  def toJson() = {
    val out: Map[String, Any] = toMap ++ (histogram match {
      case None => immutable.Map.empty[String, Any]
      case Some(h) => immutable.Map[String, Any]("histogram" -> h.get(false))
    })
    Json.build(out).toString
  }

  override def equals(other: Any) = other match {
    case t: TimingStat =>
      t.count == count && t.maximum == maximum && t.minimum == minimum && t.sum == sum &&
        t.sumSquares == sumSquares
    case _ => false
  }

  override def toString = {
    val out = toMap
    out.keys.sorted.map { key => "%s=%d".format(key, out(key)) }.mkString("(", ", ", ")")
  }

  private def toMapWithoutHistogram = {
    immutable.Map[String, Long]("count" -> count, "maximum" -> maximum, "minimum" -> minimum,
                                "sum" -> sum, "sum_squares" -> sumSquares, "average" -> average,
                                "standard_deviation" -> standardDeviation)
  }

  def toMap: immutable.Map[String, Long] = {
    toMapWithoutHistogram ++ (histogram match {
      case None => immutable.Map.empty[String, Long]
      case Some(h) => immutable.Map[String, Long]("p25" -> h.getPercentile(0.25),
                                                  "p50" -> h.getPercentile(0.5),
                                                  "p75" -> h.getPercentile(0.75),
                                                  "p90" -> h.getPercentile(0.9),
                                                  "p99" -> h.getPercentile(0.99),
                                                  "p999" -> h.getPercentile(0.999),
                                                  "p9999" -> h.getPercentile(0.9999))
    })
  }
}
