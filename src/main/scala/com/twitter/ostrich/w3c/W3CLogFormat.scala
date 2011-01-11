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

package com.twitter.ostrich.w3c

import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}
import java.util.zip.CRC32
import scala.collection.Map
import com.twitter.xrayspecs.Time
import com.twitter.xrayspecs.TimeConversions._


object W3CLogFormat {
  protected val formatter = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss")
  formatter.setTimeZone(TimeZone.getTimeZone("GMT+0000"))
}


/**
 * A log format for "w3c-style" lines.
 */
class W3CLogFormat(val printCrc: Boolean) extends LogFormat {
  import W3CLogFormat._

  private val date = new Date(0)
  private val crcGenerator = new CRC32()
  private var headerCrc = 0L  // Crc at the last call to generateHeader.
  private var previousHeaderCrc = 0L  // Crc of the one-but-last call to generateHeader.


  def generateLine(orderedKeys: Iterable[String], vals: Map[String, Any]): String = {
    val rv = orderedKeys.map { key => vals.get(key).map { stringify(_) }.getOrElse("-") }.mkString(" ")
    if (printCrc) headerCrc + " " + rv else rv
  }

  override def generateHeader(orderedKeys: Iterable[String]): Option[String] = {
    previousHeaderCrc = headerCrc
    val fieldsHeader = orderedKeys.mkString("#Fields: ", " ", "")
    headerCrc = crc32(fieldsHeader)
    date.setTime(Time.now.inMilliseconds)  // We set the time to now explicitly so we can fix a time in tests.
    Some(Array("#Version: 1.0", "\n",
               "#Date: ", formatter.format(date), "\n",
               "#CRC: ", headerCrc.toString, "\n",
               fieldsHeader, "\n").mkString(""))
  }

  override def headerChanged: Boolean = headerCrc != previousHeaderCrc

  private def crc32(header: String): Long = {
    crcGenerator.reset()
    crcGenerator.update(header.getBytes("UTF-8"))
    crcGenerator.getValue()
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
