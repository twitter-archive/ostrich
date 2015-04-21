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

  private var histogram = new Histogram()

  /**
   * Resets the state of this Metric. Clears all data points collected so far.
   */
  def clear() {
    histogram.clear()
  }

  /**
   * Adds a data point.
   */
  def add(n: Int): Long = {
    if (n > -1) {
      histogram.add(n)
    } else {
      log.debug("Tried to add a negative data point.")
      histogram.count
    }
  }

  /**
   * Add a summarized set of data points.
   */
  def add(distribution: Distribution): Long = synchronized {
    histogram.merge(distribution.histogram)
    histogram.count
  }

  override def clone(): Metric = {
    val rv = new Metric
    rv.histogram = histogram.clone()
    rv
  }

  /**
   * Returns a Distribution for this Metric.
   */
  def apply(): Distribution = synchronized { histogram() }
}

class FanoutMetric(others: Metric*) extends Metric {
  override def clear() {
    others.foreach { _.clear() }
    super.clear()
  }

  override def add(n: Int) = synchronized {
    others.foreach { _.add(n) }
    super.add(n)
  }

  override def add(distribution: Distribution) = synchronized {
    others.foreach { _.add(distribution) }
    super.add(distribution)
  }
}
