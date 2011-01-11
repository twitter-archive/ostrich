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

import net.lag.logging.Logger
import scala.collection.mutable
import java.util.Date
import java.net.InetAddress

// TODO(benjy): Do we really need four different classes for interaction with a log??
// (LogEntry, LogReporter, PerThreadStats and StatsLogger!!)

/**
 * Implementation of a log line. For each request or unit of work, create a new LogEntry instance and
 * use it to track how long work is taking or other metrics about your request. Once you're finished, simply
 * flush the entry.
 *
 * @params logger the Logger to write the entry to
 * @params fields the name of the fields that will be output. If you attempt to write to a column
 *   that doesn't exist, it will not appear in the log.
 */
class LogEntry(val logger: Logger, val logFormat: LogFormat, val fields: Array[String]) extends StatsProvider {
  val log = Logger.get(getClass.getName)
  val fieldNames: Set[String] = Set.empty ++ fields

  protected[ostrich] val map: mutable.Map[String, Any] = new mutable.HashMap[String, Any] {
    override def initialSize = fields.length * 2
  }

  /**
   * Temporary Map of Timing info (things that have been started but not finished yet.
   * It is expected that endTiming will remove the entry from this map.
   */
  protected[ostrich] val timingMap: mutable.Map[String, Long] = new mutable.HashMap[String, Long] {
    override def initialSize = fields.length * 2
  }

  def clearAll() = map.clear()

  def getCounterStats(reset: Boolean) = Stats.getCounterStats(reset)
  def getTimingStats(reset: Boolean) = Stats.getTimingStats(reset)

  def addTiming(name: String, duration: Int): Long = {
    log(name, duration)
    Stats.addTiming(name, duration)
  }

  /**
   * TimingStats don't fit naturally into a logfile entry so they are only logged globally.
   */
  def addTiming(name: String, timingStat: TimingStat): Long = {
    // can't really log these. TODO(benjy): Revisit this?
    Stats.addTiming(name, timingStat)
  }

  /**
   * Private method to ensure that fields being inserted are actually being tracked, logging an error otherwise.
   */
  private def log_safe[T](name: String, value: T) {
    if (!fieldNames.contains(name)) {
      log.error("trying to log unregistered field: %s".format(name))
    }
    map + (name -> value)
  }

  def log(name: String, value: String) {
    log_safe(name, value)
  }

  /**
   * Adds the current name, timing pair to the stats map.
   */
  def log(name: String, timing: Long) {
    log_safe(name, map.getOrElse(name, 0L).asInstanceOf[Long] + timing)
  }

  def log(name: String, date: Date) {
    log_safe(name, date)
  }

  def log(name: String, ip: InetAddress) {
    log_safe(name, ip)
  }

  def incr(name: String, count: Int) = {
    log_safe(name, map.getOrElse(name, 0L).asInstanceOf[Long] + count)
    Stats.incr(name, count)
  }

  /**
   * Returns a w3c logline containing all known fields.
   */
  def log_entry: String = logFormat.generateLine(fields, map)

  /**
   * When the execution context is completed, flush it to stable storage.
   */
  def flush() = {
    logger.info(log_entry)
    clearAll()
  }

  /**
   * For timing work that doesn't fall into one lexical area, you can specify a specific start and end.
   */
  def startTiming(name: String) {
    if (map.contains(name)) {
      log.warning("adding timing for an already timed column")
    }
    timingMap += (name -> System.currentTimeMillis)
  }

  def endTiming(name: String): Unit = timingMap.get(name) match {
    case None => log.error("endTiming called for name that had no start time: %s", name)
    case Some(start) => {
      val startTime = start.asInstanceOf[Long]
      addTiming(name, (System.currentTimeMillis - startTime).toInt)
      timingMap -= name
    }
  }
}
