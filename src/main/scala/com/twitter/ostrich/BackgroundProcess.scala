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

import java.util.concurrent.CountDownLatch
import net.lag.logging.Logger


/**
 * Generalization of a background process that runs in a thread, and can be
 * stopped. Stopping the thread waits for it to finish running.
 *
 * The code block will be run inside a "forever" loop, so it should either
 * call a method that can be interrupted (like sleep) or block for a low
 * timeout.
 */
abstract class BackgroundProcess(name: String) extends Thread(name) {
  private val log = Logger.get

  @volatile var running = false
  val startLatch = new CountDownLatch(1)

  override def start() {
    running = true
    super.start()
    startLatch.await()
  }

  override def run() {
    startLatch.countDown()
    while (running) {
      try {
        runLoop()
      } catch {
        case e: InterruptedException =>
          log.info("Background process %s exiting by request.", name)
          running = false
        case e: Throwable =>
          log.error(e, "Background process %s died with unexpected exception: %s", name, e)
          running = false
      }
    }
  }

  def runLoop()

  def shutdown() {
    running = false
    interrupt()
    join()
  }
}
