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

import java.util.Date
import scala.collection.Map
import scala.collection.immutable.HashMap
import com.twitter.conversions.time._
import com.twitter.logging._
import com.twitter.util.Time
import org.specs._

class LogReporterSpec extends Specification {
  class TestLogFormat extends LogFormat {
    def generateLine(orderedKeys: Iterable[String], vals: Map[String, Any]): String = {
      vals.mkString("Line: ", ",", "")
    }

    override def generateHeader(orderedKeys: Iterable[String]): Option[String] = {
      Some(orderedKeys.mkString("Header: ", ",", ""))
    }

    var newHeader: Boolean = false
    override def headerChanged: Boolean = newHeader
  }

  "LogReporter" should {
    val logger = Logger.get("testlog")
    logger.setLevel(Level.INFO)

    val handler = new StringHandler(BareFormatter, None)
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)

    var format: TestLogFormat = null
    var reporter: LogReporter = null

    doBefore {
      handler.clear()
      format = new TestLogFormat()
      reporter = new LogReporter(logger, format)
    }

    "not repeat the header too often" in {
      reporter.report(HashMap("a" -> 1))
      reporter.report(HashMap("a" -> 2))
      reporter.nextHeaderDumpAt = Time.now
      reporter.report(HashMap("a" -> 3))
      handler.get.split("\n").toList mustEqual
        "Header: a" ::
        "Line: a -> 1" ::
        "Line: a -> 2" ::
        "Header: a" ::
        "Line: a -> 3" :: Nil
    }

    "repeat the header when the fields change" in {
      reporter.report(HashMap("a" -> 1))
      reporter.report(HashMap("a" -> 2))
      format.newHeader = true
      reporter.report(HashMap("a" -> 3, "b" -> 1))
      handler.get.split("\n").toList mustEqual
        "Header: a" ::
        "Line: a -> 1" ::
        "Line: a -> 2" ::
        "Header: a,b" ::
        "Line: a -> 3,b -> 1" :: Nil
    }
  }
}

