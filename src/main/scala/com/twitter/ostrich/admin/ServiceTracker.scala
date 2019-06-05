/*
 * Copyright 2010 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.ostrich.admin

import scala.collection.mutable

/**
 * Single server object that can track multiple Service implementations and multiplex the
 * shutdown & quiesce commands.
 */
object ServiceTracker {
  private val services = new mutable.HashSet[Service]

  def clearForTests() {
    services.clear()
  }

  def peek = services.toList

  def register(service: Service) {
    synchronized {
      services += service
    }
  }

  def shutdown() {
    synchronized {
      val rv = services.toList
      services.clear()
      rv
    }.foreach { _.shutdown() }
  }

  def quiesce() {
    synchronized { services.toList }.foreach { _.quiesce() }
  }

  def reload() {
    synchronized { services.toList }.foreach { _.reload() }
  }
}
