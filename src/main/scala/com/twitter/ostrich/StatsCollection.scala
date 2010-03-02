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

import scala.collection.{Map, jcl, mutable, immutable}
import java.util.concurrent.ConcurrentHashMap

/**
 * Concrete StatsProvider that tracks counters and timings.
 */
class StatsCollection extends StatsProvider {
  private val counterMap = new mutable.HashMap[String, Counter]()
  private val timingMap = new ConcurrentHashMap[String, Timing]()

  def addTiming(name: String, duration: Int): Long = {
    getTiming(name).add(duration)
  }

  def addTiming(name: String, timingStat: TimingStat): Long = {
    getTiming(name).add(timingStat)
  }

  def incr(name: String, count: Int): Long = {
    getCounter(name).value.addAndGet(count)
  }

  def getCounterStats(reset: Boolean): Map[String, Long] = {
    val rv = immutable.HashMap(counterMap.map { case (k, v) => (k, v.value.get) }.toList: _*)
    if (reset) {
      for ((k, v) <- counterMap) {
        v.reset()
      }
    }
    rv
  }

  def getTimingStats(reset: Boolean): Map[String, TimingStat] = {
    val out = new mutable.HashMap[String, TimingStat]
    for ((key, timing) <- jcl.Map(timingMap)) {
      out += (key -> timing.get(reset))
    }
    out
  }

  def clearAll() {
    counterMap.synchronized { counterMap.clear() }
    timingMap.clear()
  }

  /**
   * Find or create a counter with the given name.
   */
  def getCounter(name: String): Counter = counterMap.synchronized {
    counterMap.get(name) match {
      case Some(counter) => counter
      case None =>
        val counter = new Counter
        counterMap += (name -> counter)
        counter
    }
  }

  /**
   * Find or create a timing measurement with the given name.
   */
  def getTiming(name: String): Timing = {
    var timing = timingMap.get(name)
    while (timing == null) {
      timing = timingMap.putIfAbsent(name, new Timing)
      timing = timingMap.get(name)
    }
    timing
  }
}
