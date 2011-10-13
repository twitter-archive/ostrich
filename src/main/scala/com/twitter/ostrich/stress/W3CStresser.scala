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
package stress

import java.text.SimpleDateFormat
import java.util.Date
import com.twitter.logging.{BareFormatter, Level, Logger, StringHandler}
import stats.W3CStats

object W3CStresser {
  /**
   * Generates 100k w3c lines, each being 1k columns wide, writes them to an in-memory StringHandler
   * so we're only CPU bound.
   */
  def main(args: Array[String]) {
    val logger = Logger.get("w3c")
    logger.setLevel(Level.INFO)
    val handler = new StringHandler(BareFormatter, None)
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)

    val thousandInts = (1 until 1000).toArray
    val thousandColumns = thousandInts.map { x: Int => x.toString }
    val hundredThousand = (1 until 100000).toArray
    val w3c = new W3CStats(logger, thousandColumns, false)

    println("%s Starting to stress our W3C deals".format(new Date()))
    hundredThousand.foreach { i =>
      if (i % 10000 == 0) { println("%s finished our %d'th run".format(new Date(), i)) }
      w3c { stats =>
        thousandInts.foreach { j => stats.addMetric(j.toString, j) }
      }
      handler.clear() // based on our testing, this does not add substantial CPU pressure but reduces memory needs for the test.
    }
    println("%s Done stressing".format(new Date()))
  }
}
