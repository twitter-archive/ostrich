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

package com.twitter.ostrich

/**
 * A thing that can be asked to turn itself into json.
 */
trait JsonSerializable {
  def toJson(): String
}

object Json {
  private def quotedChar(codePoint: Int) = {
    codePoint match {
      case c if c > 0xffff =>
        val chars = Character.toChars(c)
        "\\u%04x\\u%04x".format(chars(0).toInt, chars(1).toInt)
      case c if c > 0x7e =>
        "\\u%04x".format(c.toInt)
      case c =>
        c.toChar
    }
  }

  private def quote(s: String) = {
    val charCount = s.codePointCount(0, s.length)
    "\"" + 0.to(charCount - 1).map { idx =>
      s.codePointAt(s.offsetByCodePoints(0, idx)) match {
        case 0x0d => "\\r"
        case 0x0a => "\\n"
        case 0x09 => "\\t"
        case 0x22 => "\\\""
        case 0x5c => "\\\\"
        case 0x2f => "\\/"     // to avoid sending "</"
        case c => quotedChar(c)
      }
    }.mkString("") + "\""
  }

  /**
   * Returns a JSON representation of the given object.
   */
  def build(obj: Any): String = {
    obj match {
      case null =>
        "null"
      case x: Boolean =>
        x.toString
      case x: Number =>
        x.toString
      case array: Array[_] =>
        array.map(build(_)).mkString("[", ",", "]")
      case list: Seq[_] =>
        list.map(build(_)).mkString("[", ",", "]")
      case map: scala.collection.Map[_, _] =>
        map.toSeq.map { case (k, v) => (k.toString, build(v)) }.sorted.map { case (k, v) => quote(k) + ":" + v }.mkString("{", ",", "}")
      case x: JsonSerializable =>
        x.toJson()
      case x =>
        quote(x.toString)
    }
  }
}
