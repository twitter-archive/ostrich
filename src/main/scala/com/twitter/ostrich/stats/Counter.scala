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

package com.twitter.ostrich.stats

import com.twitter.jsr166e.LongAdder
import java.util.concurrent.atomic.AtomicLong

/**
 * A Counter simply keeps track of how many times an event occurred.
 * All operations are atomic and thread-safe.
 */
class Counter(value: LongAdder) {
  def this() = this(new LongAdder())

  /**
   * Increment the counter by one and return the current value.
   */
  @deprecated("Use increment or incrementAndGet")
  def incr(): Long = incrementAndGet()

  /**
   * Increment the counter by `n`.
   */
  @deprecated("Use increment or incrementAndGet")
  def incr(n: Int): Long = incrementAndGet(n)

  /**
   * Increment the counter by one and return the current value.
   * This is not an atomic operation.
   */
  def incrementAndGet() : Long = {
    increment()
    value.longValue()
  }

  /**
   * Increment the counter by 'n' and return the current value.
   * This is not an atomic operation.
   */
  def incrementAndGet(n: Int) : Long = {
    increment(n)
    value.longValue()
  }

  /**
   * Increment the counter by one.
   */
  def increment() : Unit = value.increment()

  /**
   * Increment the counter by 'n'.
   */
  def increment(n: Int) : Unit = value.add(n)

  /**
   * Get the current value.
   */
  def apply(): Long = value.longValue()

  /**
   * Set a new value, wiping the old one.
   */
  def update(n: Long) = {
    value.sumThenReset()
    value.add(n)
  }

  /**
   * Clear the counter back to zero.
   */
  def reset() : Long = value.sumThenReset()

  override def toString() = "Counter(%d)".format(value.longValue())
}

/**
 * A Counter that sends modifications to a set of "fanout" counters also.
 */
class FanoutCounter(others: Counter*) extends Counter {
  override def incr() = {
    others.foreach { _.incr() }
    super.incr()
  }

  override def incr(n: Int) = {
    others.foreach { _.incr(n) }
    super.incr(n)
  }

  override def update(n: Long) = {
    others.foreach { _.update(n) }
    super.update(n)
  }

  override def reset() = {
    others.foreach { _.reset() }
    super.reset()
  }
}
