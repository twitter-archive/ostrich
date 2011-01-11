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

import scala.collection.Map
import scala.util.Sorting._
import com.twitter.xrayspecs.Time
import com.twitter.xrayspecs.TimeConversions._
import net.lag.logging.Logger

// TODO(benjy): Do we really need four different classes for interaction with a log??
// (LogEntry, LogReporter, PerThreadStats and StatsLogger!!)

/**
 * Log lines to a java logger, using a map of key/value pairs.
 *
 * On each call to `report`, if the keys in the map have changed, or it's been "a while" since the header was
 * last logged, the header is logged again.
 */
class LogReporter(val logger: Logger, val logFormat: LogFormat) {

  // The header lines will be written out this often, even if the fields haven't changed.
  // (This lets log parsers resynchronize after a failure.)
  private var headerRepeatFrequencyInMilliseconds = 5 * 60 * 1000
  var nextHeaderDumpAt = Time.now  // TODO(benjy): Should be private, but the test currently accesses it.

  /**
   * Write a log line to the log. If the field names differ from the previously-logged line,
   * a new header block will be written.
   */
  def report(vals: Map[String, Any]): Unit = {
    val orderedKeys = vals.keys.toList.sort(_ < _)
    val header = logFormat.generateHeader(orderedKeys)
    if (header != None && (logFormat.headerChanged || Time.now >= nextHeaderDumpAt)) {
      logger.info(header.getOrElse(""))
      nextHeaderDumpAt = headerRepeatFrequencyInMilliseconds.milliseconds.fromNow
    }
    logger.info(logFormat.generateLine(orderedKeys, vals))
  }
}
