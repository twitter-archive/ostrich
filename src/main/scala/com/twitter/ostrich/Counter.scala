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

import java.util.concurrent.atomic.AtomicLong


/**
 * A Counter is a measure that simply keeps track of how
 * many times an event occurred.
 */
class Counter {
  var value = new AtomicLong

  def incr() = value.incrementAndGet
  def incr(n: Int) = value.addAndGet(n)
  def apply(reset: Boolean) = if (reset) value.getAndSet(0) else value.get()
  def apply(): Long = this(false)
  def update(n: Long) = value.set(n)
  def reset() = update(0L)
}
