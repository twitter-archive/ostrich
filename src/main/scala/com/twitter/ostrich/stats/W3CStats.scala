/*
 * Copyright 2009 Twitter, Inc.
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

package com.twitter.ostrich
package stats

import java.util.TimeZone
import java.util.zip.CRC32
import scala.collection.Map
import scala.collection.mutable
import com.twitter.conversions.time._
import com.twitter.logging.Logger
import com.twitter.util.{Time, TwitterDateFormat}

/**
 * Dump "w3c" style stats to a logger.
 *
 * This may be used as a periodic dump (by calling `write` directly) or to dump stats within a
 * work unit, using the `TransactionalStatsCollection` API.
 */
class W3CStats(logger: Logger, fields: Array[String], val printCrc: Boolean)
extends TransactionalStatsCollection {
  private val crcGenerator = new CRC32()
  private val formatter = TwitterDateFormat("dd-MMM-yyyy HH:mm:ss")
  formatter.setTimeZone(TimeZone.getTimeZone("GMT+0000"))

  private var headerCrc = 0L  // Crc at the last call to generateHeader.
  private var previousHeaderCrc = 0L  // Crc of the one-but-last call to generateHeader.

  // The header lines will be written out this often, even if the fields haven't changed.
  // (This lets log parsers resynchronize after a failure.)
  private val headerRepeatFrequency = 5.minutes
  private var nextHeaderDumpAt = Time.now

  def write(summary: StatsSummary) {
    val flatmap = flatten(summary)
    val fieldList: Seq[String] = if (fields.size > 0) fields.toSeq else flatmap.keys.toSeq.sorted
    val fieldsHeader = fieldList.mkString("#Fields: ", " ", "")
    headerCrc = crc32(fieldsHeader)
    if (headerCrc != previousHeaderCrc || Time.now >= nextHeaderDumpAt) {
      logger.info(generateHeader(fieldsHeader))
      nextHeaderDumpAt += headerRepeatFrequency
    }
    logger.info(generateLine(fieldList, flatmap))
  }

  private def crc32(header: String): Long = {
    crcGenerator.reset()
    crcGenerator.update(header.getBytes("UTF-8"))
    crcGenerator.getValue()
  }

  def generateHeader(fieldsHeader: String) = {
    previousHeaderCrc = headerCrc
    headerCrc = crc32(fieldsHeader)
    Array("#Version: 1.0", "\n",
          "#Date: ", formatter.format(Time.now.toDate), "\n",
          "#CRC: ", headerCrc.toString, "\n",
          fieldsHeader).mkString("")
  }

  private def flatten(summary: StatsSummary): Map[String, Any] = {
    val flatmap = new mutable.HashMap[String, Any]
    flatmap ++= summary.counters
    summary.metrics.foreach { case (k1, d) =>
      // w3c logs don't want percentiles (for now?)
      d.toMapWithoutPercentiles.foreach { case (k2, v) => flatmap(k1 + "_" + k2) = v }
    }
    flatmap ++= summary.gauges
    flatmap ++= summary.labels
    flatmap
  }

  private def generateLine(fieldList: Seq[String], flatmap: Map[String, Any]): String = {
    val rv = fieldList.map { key => flatmap.get(key).map { stringify(_) }.getOrElse("-") }.mkString(" ")
    if (printCrc) (headerCrc + " " + rv) else rv
  }

  private def stringify(value: Any): String = value match {
    case s: String => s
    case l: Long => l.toString()
    case i: Int => i.toString()
    case d: Double => d.toString()
    case _ => "-"
  }
}
