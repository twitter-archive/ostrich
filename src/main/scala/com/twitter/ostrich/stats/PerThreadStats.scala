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

import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.{Date, HashSet}
import java.util.zip.CRC32
import scala.collection.mutable
import com.twitter.logging.Logger

// TODO(benjy): Do we really need four different classes for interaction with a log??
// (LogEntry, LogReporter, PerThreadStats and StatsLogger!!)

/**
 * Convenience methods for per-thread timing blocks and exporting those timings in a log.
 *
 * @param fields The fields, in order, as they will appear in the final log output.
 */
class PerThreadStats(val logger: Logger, val logFormat: LogFormat, val fields: Array[String]) extends StatsProvider {
  val log = Logger.get(getClass.getName)
  var complainAboutUnregisteredFields = true
  val fieldNames: Set[String] = Set.empty ++ fields

  /**
   * Store our map of named events.
   */
  private val tl = new ThreadLocal[mutable.Map[String, Any]]() {
    override def initialValue(): mutable.Map[String, Any] = new mutable.HashMap[String, Any] {
      override def initialSize = (fields.length max 1) * 2
    }
  }

  /**
   * Returns the current map containing this thread's logging stats.
   */
  def getStats(): mutable.Map[String, Any] = tl.get()

  /**
   * Resets this thread's w3c stats knowledge.
   */
  def clearAll(): Unit = getStats().clear()

  /**
   * Private method to ensure that fields being inserted are actually being tracked, logging an error otherwise.
   */
  private def log_safe[T](name: String, value: T) {
    if (complainAboutUnregisteredFields && !fieldNames.contains(name)) {
      log.error("trying to log unregistered field: %s".format(name))
    }
    getStats() += (name -> value)
  }

  /**
   * Adds the current name, value pair to the stats map.
   */
  def log(name: String, value: String) {
    log_safe(name, getStats().get(name).map(_ + "," + value).getOrElse(value))
  }

  /**
   * Adds the current name, timing pair to the stats map.
   */
  def log(name: String, timing: Long) {
    log_safe(name, getStats().getOrElse(name, 0L).asInstanceOf[Long] + timing)
  }

  def log(name: String, date: Date) {
    log_safe(name, date)
  }

  def log(name: String, ip: InetAddress) {
    log_safe(name, ip)
  }

  /**
   * Returns a logline containing all known fields.
   */
  def log_entry: String = logFormat.generateLine(fields, getStats())

  override def addMetric(name: String, duration: Int) {
    log(name, duration)
    Stats.addMetric(name, duration)
  }

  override def addMetric(name: String, distribution: Distribution) {
    // can't really log these. TODO(benjy): This was true for W3C, but now that we've generalized the logging facility
    // to multiple formats, perhaps we should revisit this.
    Stats.addMetric(name, distribution)
  }

  override def incr(name: String, count: Int) = {
    log_safe(name, getStats().getOrElse(name, 0L).asInstanceOf[Long] + count)
    Stats.incr(name, count)
  }

  def getCounter(name: String) = Stats.getCounter(name)
  def getMetric(name: String) = Stats.getMetric(name)
  def getGauge(name: String) = Stats.getGauge(name)
  def getCounters() = Stats.getCounters()
  def getMetrics() = Stats.getMetrics()
  def getGauges() = Stats.getGauges()
  def addGauge(name: String)(gauge: => Double) = Stats.addGauge(name)(gauge)
  def clearGauge(name: String) = Stats.clearGauge(name)

  def setLabel(name: String, value: String) {  }
  def clearLabel(name: String) {  }
  def getLabel(name: String) = None
  def getLabels() = null

  /**
   * Coalesce all events (counters, timings, etc.) that happen in this thread within this
   * transaction, and log them as a single line at the end. This is useful for logging everything
   * that happens within an HTTP request/response cycle, or similar.
   */
  def transaction[T](f: => T): T = {
    clearAll()
    try {
      f
    } finally {
      logger.info(log_entry)
    }
  }
}
