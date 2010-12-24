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

import scala.collection.{Map, JavaConversions, mutable, immutable}
import scala.collection.JavaConversions.JConcurrentMapWrapper
import java.util.concurrent.ConcurrentHashMap


object StatsCollection {
  /**
   * Returns a new StatsCollection with the keys of the original StatsCollection
   * already created.
   */
  def shallowClone(original: StatsCollection): StatsCollection = {
    val stats = new StatsCollection

    for (key <- original.getCounterKeys) {
      stats.getCounter(key)
    }

    for (key <- original.getTimingKeys) {
      stats.getTiming(key)
    }

    stats
  }
}

/**
 * Concrete StatsProvider that tracks counters and timings.
 */
class StatsCollection extends StatsProvider {
  private val counterMap = new JConcurrentMapWrapper(new ConcurrentHashMap[String, Counter]())
  private val timingMap = new JConcurrentMapWrapper(new ConcurrentHashMap[String, Timing]())

  def addTiming(name: String, duration: Int): Long = {
    getTiming(name).add(duration)
  }

  def addTiming(name: String, timingStat: TimingStat): Long = {
    getTiming(name).add(timingStat)
  }

  def incr(name: String, count: Int): Long = {
    getCounter(name).value.addAndGet(count)
  }

  def getCounterKeys(): Iterable[String] = {
    counterMap.keySet
  }

  def getCounterStats(reset: Boolean): Map[String, Long] = {
    val rv = new mutable.HashMap[String, Long]
    for ((key, counter) <- counterMap) {
      rv += (key -> counter(reset))
    }
    rv
  }

  def getTimingKeys(): Iterable[String] = {
    timingMap.keySet
  }

  def getTimingStats(reset: Boolean): Map[String, TimingStat] = {
    val out = new mutable.HashMap[String, TimingStat]
    for ((key, timing) <- timingMap) {
      out += (key -> timing.get(reset))
    }
    out
  }

  def clearAll() {
    counterMap.clear()
    timingMap.clear()
  }

  /**
   * Find or create a counter with the given name.
   */
  def getCounter(name: String): Counter = {
    var counter = counterMap.get(name)
    while (counter.isEmpty) {
      counterMap.putIfAbsent(name, new Counter)
      counter = counterMap.get(name)
    }
    counter.get
  }

  /**
   * Find or create a timing measurement with the given name.
   */
  def getTiming(name: String): Timing = {
    var timing = timingMap.get(name)
    while (timing.isEmpty) {
      timingMap.putIfAbsent(name, new Timing)
      timing = timingMap.get(name)
    }
    timing.get
  }
}
