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
    def flatten: String = {
      obj match {
        case s: Seq[_] =>
          s.mkString(", ")
        case m: Map[_, _] =>
          Sorting.stableSort(m.keys.toList, { (a: Any, b: Any) => a.toString < b.toString }).map { k =>
            m(k) match {
              case m: Map[_, _] =>
                "%s:\n".format(k) + new RichAny(m).flatten.split("\n").map { "  " + _ }.mkString("\n")
              case x =>
                "%s: %s".format(k, new RichAny(x).flatten)
            }
          }.mkString("\n")
        case x =>
          x.toString
      }
    }
  }
  implicit def richAny(obj: Any) = new RichAny(obj)
}
