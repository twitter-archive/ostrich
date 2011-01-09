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

import java.util.concurrent.atomic.AtomicLong

/**
 * A Counter is simply keeps track of how many times an event occurred.
 */
class Counter {
  val value = new AtomicLong()

  def incr() = value.incrementAndGet
  def incr(n: Int) = value.addAndGet(n)
  def apply(): Long = value.get()
  def update(n: Long) = value.set(n)
  def reset() = update(0L)

  override def toString() = "Counter(%d)".format(value.get())
}
