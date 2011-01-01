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
package stats

import scala.collection.mutable
import com.twitter.logging.Logger

/**
 * A Metric collates data points and can report a Distribution.
 */
class Metric {
  val log = Logger.get(getClass.getName)

  private var maximum = Int.MinValue
  private var minimum = Int.MaxValue
  private var count: Int = 0
  private var histogram = new Histogram()
  private var mean: Double = 0.0

  /**
   * Resets the state of this Metric. Clears all data points collected so far.
   */
  def clear() = synchronized {
    maximum = Int.MinValue
    minimum = Int.MaxValue
    count = 0
    histogram.clear()
  }

  /**
   * Adds a data point.
   */
  def add(n: Int): Long = synchronized {
    if (n > -1) {
      maximum = n max maximum
      minimum = n min minimum
      count += 1
      histogram.add(n)
      if (count == 1) {
        mean = n
      } else {
        mean += (n.toDouble - mean) / count
      }
    } else {
      log.warning("Tried to add a negative data point.")
    }
    count
  }

  /**
   * Add a summarized set of data points.
   */
  def add(distribution: Distribution): Long = synchronized {
    if (distribution.count > 0) {
      // these equations end up using the sum again, and may be lossy. i couldn't find or think of
      // a better way.
      mean = (mean * count + distribution.mean * distribution.count) / (count + distribution.count)
      count += distribution.count
      maximum = distribution.maximum max maximum
      minimum = distribution.minimum min minimum
      distribution.histogram.map { h => histogram.merge(h) }
    }
    count
  }

  /**
   * Returns a Distribution for this Metric.
   */
  def apply(reset: Boolean): Distribution = synchronized {
    val rv = new Distribution(count, maximum, minimum, Some(histogram.clone()), mean)
    if (reset) clear()
    rv
  }
}

class FanoutMetric extends Metric {
  private val fanout = new mutable.HashSet[Metric]

  def addFanout(metric: Metric) {
    fanout += metric
  }

  override def clear() {
    synchronized {
      super.clear()
      fanout.foreach { _.clear() }
    }
  }

  override def add(n: Int) = synchronized {
    fanout.foreach { _.add(n) }
    super.add(n)
  }

  override def add(distribution: Distribution) = synchronized {
    fanout.foreach { _.add(distribution) }
    super.add(distribution)
  }
}
