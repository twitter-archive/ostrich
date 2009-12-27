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


object BackgroundProcess {
  val log = Logger.get(getClass.getName)

  /**
   * Spawn a short-lived thread for a throw-away task.
   */
  def spawn(threadName: String, daemon: Boolean)(f: => Unit): Thread = {
    val thread = new Thread(threadName) {
      override def run() {
        try {
          f
        } catch {
          case e: Throwable =>
            log.error(e, "Spawned thread %s died with a terminal exception", Thread.currentThread)
        }
      }
    }
    thread.setDaemon(daemon)
    thread.start
    thread
  }

  def spawn(threadName: String)(f: => Unit): Thread = spawn(threadName, false)(f)
  def spawnDaemon(threadName: String)(f: => Unit): Thread = spawn(threadName, true)(f)
}


/**
 * Generalization of a background process that runs in a thread, and can be
 * stopped. Stopping the thread waits for it to finish running.
 *
 * The code block will be run inside a "forever" loop, so it should either
 * call a method that can be interrupted (like sleep) or block for a low
 * timeout.
 */
abstract class BackgroundProcess(name: String) extends Thread(name) with ServerInterface {
  private val log = Logger.get

  @volatile var running = false
  val startLatch = new CountDownLatch(1)

  override def start() {
    log.info("Starting %s", name)
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
          log.info("%s exiting by request.", name)
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

  def quiesce() {
    shutdown()
  }
}
