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
package stats

import java.net.InetAddress
import scala.collection.immutable
import com.twitter.conversions.time._
import com.twitter.logging.Logger
import com.twitter.util.{Duration, Time}
import admin.PeriodicBackgroundProcess

/**
 * Log all collected stats as a json line to a java logger at a regular interval.
 */
class JsonStatsLogger(val logger: Logger, val period: Duration, val serviceName: Option[String],
                      collection: StatsCollection)
extends PeriodicBackgroundProcess("JsonStatsLogger", period) {
  def this(logger: Logger, period: Duration) = this(logger, period, None, Stats)

  val listener = new StatsListener(collection)
  val hostname = InetAddress.getLocalHost().getCanonicalHostName()

  def periodic() {
    val stats = listener.get()
    val statMap =
      stats.counters ++
      stats.gauges.map { case (key, d) =>
        if (d.longValue == d) { (key, d.longValue) } else { (key, d) }
      } ++
      stats.metrics.flatMap { case (key, distribution) =>
        distribution.toMap.map { case (subkey, value) =>
          (key + "_" + subkey, value)
        }
      } ++
      Map(
        "service" -> serviceName.getOrElse("unknown"),
        "source" -> hostname,
        "timestamp" -> Time.now.inSeconds
      )
    val cleanedKeysStatMap = statMap.map { case (key, value) => (key.replaceAll(":", "_"), value) }

    logger.info(Json.build(Map(cleanedKeysStatMap.toSeq: _*)).toString)
  }
}
