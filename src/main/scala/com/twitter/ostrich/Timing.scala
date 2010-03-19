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

import net.lag.logging.Logger


/**
 * A Timing collates durations of an event and can report
 * min/max/avg along with how often the event occurred.
 */
class Timing {
  val log = Logger.get(getClass.getName)

  private var maximum = Math.MIN_INT
  private var minimum = Math.MAX_INT
  private var sum: Long = 0
  private var sumSquares: Long = 0
  private var count: Int = 0
  private var histogram = new Histogram()

  /**
   * Resets the state of this Timing. Clears the durations and counts collected so far.
   */
  def clear() = synchronized {
    maximum = Math.MIN_INT
    minimum = Math.MAX_INT
    sum = 0
    sumSquares = 0
    count = 0
    histogram.clear()
  }

  /**
   * Adds a duration to our current Timing.
   */
  def add(n: Int): Long = synchronized {
    if (n > -1) {
      maximum = n max maximum
      minimum = n min minimum
      sum += n
      sumSquares += (n.toLong * n)
      count += 1
      histogram.add(n)
    } else {
      log.warning("Tried to add a negative timing duration. Was the clock adjusted?")
    }
    count
  }

  /**
   * Add a summarized set of timings.
   */
  def add(timingStat: TimingStat): Long = synchronized {
    if (timingStat.count > 0) {
      maximum = timingStat.maximum max maximum
      minimum = timingStat.minimum min minimum
      sum += timingStat.sum
      sumSquares += timingStat.sumSquares
      count += timingStat.count
      timingStat.histogram.map { h => histogram.merge(h) }
    }
    count
  }

  /**
   * Returns a TimingStat for the measured event.
   * @param reset whether to erase the current history afterwards
   */
  def get(reset: Boolean): TimingStat = synchronized {
    val rv = new TimingStat(count, maximum, minimum, sum, sumSquares, Some(histogram.clone()))
    if (reset) clear()
    rv
  }
}
