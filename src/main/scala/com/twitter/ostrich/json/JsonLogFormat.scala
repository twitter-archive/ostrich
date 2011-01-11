/*
 * Copyright 2011 Twitter, Inc.
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

package com.twitter.ostrich.json

import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}
import scala.collection.Map
import scala.collection.immutable.ListMap
import com.twitter.json.Json


object JsonLogFormat {
  protected val formatter = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss")
  formatter.setTimeZone(TimeZone.getTimeZone("GMT+0000"))
}

/**
 * Log JSON lines to a java logger, using a map of key/value pairs.
 *
 * Note that the {W3C,JSON} and {Log line, Stats} logging mechanisms are screaming for a
 * refactoring to break out commonality and reduce duplication. But we're stuck with this for now.
 */
class JsonLogFormat extends LogFormat {
  import JsonLogFormat._

  def generateLine(orderedKeys: Iterable[String], vals: Map[String, Any]): String = {
    val convertedMap: Map[String, Any] =
      ListMap((orderedKeys.flatMap { key => vals.get(key).map { v => (key, convert(v)) } } ).toSeq: _*)
    Json.build(convertedMap).toString()
  }

  private def convert(value: Any): Any = value match {
    // Convert some types to string. Leave others as they are.
    case d: Date => formatter.format(d)
    case ip: InetAddress => ip.getHostAddress()
    case _ => value
  }
}

