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

package com.twitter.stats

import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton StatsCollector that collects performance data for the application.
 */
object Stats extends StatsCollection {
  includeJvmStats = true

  private val namedCollections = new ConcurrentHashMap[String, StatsCollection](128, 0.75f, 2)
  namedCollections.put("", Stats)

  /**
   * Return a named StatsCollection as defined in an AdminServiceConfig.
   * If the named collection doesn't exist, the global stats object is returned.
   */
  def get(name: String): StatsCollection = {
    val rv = namedCollections.get(name)
    if (rv == null) namedCollections.get("") else rv
  }

  /**
   * Make a named StatsCollection, or return an existing collection if one already exists under
   * that name.
   */
  def make(name: String): StatsCollection = {
    val rv = namedCollections.get(name)
    if (rv == null) {
      namedCollections.putIfAbsent(name, new StatsCollection())
    }
    namedCollections.get(name)
  }

  // helper function for computing deltas over counters
  final def delta(oldValue: Long, newValue: Long): Long = {
    if (oldValue <= newValue) {
      newValue - oldValue
    } else {
      (Long.MaxValue - oldValue) + (newValue - Long.MinValue) + 1
    }
  }

  /**
   * Create a function that returns the delta of a counter each time it's called.
   */
  def makeDeltaFunction(counter: Counter): () => Double = {
    var lastValue: Long = 0

    () => {
      val newValue = counter()
      val rv = delta(lastValue, newValue)
      lastValue = newValue
      rv.toDouble
    }
  }
}
