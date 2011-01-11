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

package com.twitter.ostrich

import scala.collection.Map

/**
 * A format for log lines.
 *
 * Note that instances may track state over multiple invocations (e.g., for formats that require line counting or
  content hashes).
 */
trait LogFormat {

  /**
   * Generates a header line for the specified keys. Override if your log format has header lines. Ignore otherwise.
   */
  def generateHeader(orderedKeys: Iterable[String]): Option[String] = None

  /*
   * You must override this if you override generateHeader.
   */
  def headerChanged: Boolean = false

  /**
   * Generates a log line reporting the specified keys, with the corresponding values from vals.
   * If the log format requires ordered keys it must use the order in the orderedKeys iterable.
   */
  def generateLine(orderedKeys: Iterable[String], vals: Map[String, Any]): String
}

