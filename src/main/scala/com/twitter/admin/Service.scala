/*
 * Copyright 2010 Twitter, Inc.
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

package com.twitter.admin

/**
 * A service is any task that can be shutdown or reloaded by the admin server.
 */
trait Service {
  /**
   * Shutdown this server.
   */
  def shutdown(): Unit

  /**
   * Stop answering new requests, and close all listening sockets, but only shutdown after the last
   * existing client dies. This is to allow servers with long-running clients to stay alive for a
   * while and service those connections, while letting another server start up and begin handling
   * new connections.
   */
  def quiesce(): Unit

  /**
   * Reload configuration, if supported by the server.
   */
  def reload() {
    // default is to do nothing.
  }
}
