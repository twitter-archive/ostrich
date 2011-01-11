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

import java.util.Date
import com.twitter.xrayspecs.Time
import com.twitter.xrayspecs.TimeConversions._
import scala.collection.Map
import scala.collection.immutable.HashMap
import net.lag.extensions._
import net.lag.logging.{GenericFormatter, Level, Logger, StringHandler}
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

    val handler = new StringHandler(new GenericFormatter("%2$s: "))
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)

    var format: TestLogFormat = null
    var reporter: LogReporter = null

    doBefore {
      Stats.clearAll()
      handler.clear()
      Time.now = Time.at("2009-08-03 19:23:04 +0000")
      format = new TestLogFormat()
      reporter = new LogReporter(logger, format)
    }

    "not repeat the header too often" in {
      reporter.report(HashMap("a" -> 1))
      reporter.report(HashMap("a" -> 2))
      reporter.nextHeaderDumpAt = Time.now
      reporter.report(HashMap("a" -> 3))
      handler.toString.split("\n").toList mustEqual
        "testlog: Header: a" ::
        "testlog: Line: (a,1)" ::
        "testlog: Line: (a,2)" ::
        "testlog: Header: a" ::
        "testlog: Line: (a,3)" :: Nil
    }

    "repeat the header when the fields change" in {
      reporter.report(HashMap("a" -> 1))
      reporter.report(HashMap("a" -> 2))
      format.newHeader = true
      reporter.report(HashMap("a" -> 3, "b" -> 1))
     handler.toString.split("\n").toList mustEqual
        "testlog: Header: a" ::
        "testlog: Line: (a,1)" ::
        "testlog: Line: (a,2)" ::
        "testlog: Header: a,b" ::
        "testlog: Line: (a,3),(b,1)" :: Nil
    }
  }
}

