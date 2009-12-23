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

import scala.collection.mutable


/**
 * Single server object that can track multiple ServerInterface implementations and multiplex the
 * shutdown & quiesce commands.
 */
object Server extends ServerInterface {
  val servers = new mutable.HashSet[ServerInterface]

  def register(server: ServerInterface) {
    servers += server
  }

  def shutdown() {
    servers.foreach { _.shutdown() }
    servers.clear()
  }

  def quiesce() {
    servers.foreach { _.quiesce() }
    servers.clear()
  }
}
