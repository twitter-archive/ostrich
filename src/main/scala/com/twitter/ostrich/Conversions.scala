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
import scala.util.Sorting


object Conversions {
  class RichAny(obj: Any) {
    private def build(obj: Any): List[String] = {
      obj match {
        case m: Map[Any, Any] =>
          Sorting.stableSort(m.keys.toList, { (a: Any, b: Any) => a.toString < b.toString }).toList.flatMap { k =>
            build(m(k)) match {
              case line :: Nil if (!line.contains(": ")) => List(k.toString + ": " + line)
              case list => (k.toString + ":") :: list.map { "  " + _ }
            }
          }
        case a: Array[_] =>
          a.flatMap { build(_) }.toList
        case s: Seq[_] =>
          s.flatMap { build(_) }.toList
        case x =>
          List(x.toString)
      }
    }

    def flatten: String = build(obj).mkString("\n") + "\n"
  }
  implicit def richAny(obj: Any): RichAny = new RichAny(obj)
}
