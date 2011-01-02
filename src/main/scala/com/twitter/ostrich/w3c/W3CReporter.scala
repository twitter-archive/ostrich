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
package w3c

import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}
import java.util.zip.CRC32
import scala.collection.Map
import scala.collection.mutable
import scala.util.Sorting._
import com.twitter.conversions.time._
import com.twitter.logging.Logger
import com.twitter.util.Time

object W3CReporter {
  protected val formatter = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss")
  formatter.setTimeZone(TimeZone.getTimeZone("GMT+0000"))
}

/**
 * Log "w3c-style" lines to a java logger, using a map of key/value pairs. On each call to
 * `report`, if the keys in the map have changed, or it's been "a while" since the header was
 * last logged, the header is logged again.
 */
class W3CReporter(val logger: Logger, val printCrc: Boolean) {
  import W3CReporter._

  def this(logger: Logger) = this(logger, false)

  /**
   * The W3C header lines will be written out this often, even if the fields haven't changed.
   * (This lets log parsers resynchronize after a failure.)
   */
  var headerRepeatFrequencyInMilliseconds = 5 * 60 * 1000

  var nextHeaderDumpAt = Time.now

  private var previousCrc = 0L

  /**
   * Write a W3C stats line to the log. If the field names differ from the previously-logged line,
   * a new header block will be written.
   */
  def report(stats: Map[String, Any]) {
    val orderedKeys = stats.keys.toList.sorted
    val fieldsHeader = orderedKeys.mkString("#Fields: ", " ", "")
    val crc = crc32(fieldsHeader)
    if (crc != previousCrc || Time.now >= nextHeaderDumpAt) {
      logHeader(fieldsHeader, crc)
    }
    logger.info(generateLine(orderedKeys, stats))
  }

  def generateLine(orderedKeys: Iterable[String], stats: Map[String, Any]) = {
    val rv = orderedKeys.map { key => stats.get(key).map { stringify(_) }.getOrElse("-") }.mkString(" ")
    if (printCrc) previousCrc + " " + rv else rv
  }

  private def logHeader(fieldsHeader: String, crc: Long) {
    val header =
      Array("#Version: 1.0",
            "#Date: " + formatter.format(new Date(Time.now.inMilliseconds)),
            "#CRC: " + crc.toString,
            fieldsHeader).mkString("\n")
    logger.info(header)
    previousCrc = crc
    nextHeaderDumpAt = headerRepeatFrequencyInMilliseconds.milliseconds.fromNow
  }

  private def crc32(header: String): Long = {
    val crc = new CRC32()
    crc.update(header.getBytes("UTF-8"))
    crc.getValue()
  }

  private def stringify(value: Any): String = value match {
    case s: String => s
    case d: Date => formatter.format(d).replaceAll(" ", "_")
    case l: Long => l.toString()
    case i: Int => i.toString()
    case ip: InetAddress => ip.getHostAddress()
    case _ => "-"
  }
}
