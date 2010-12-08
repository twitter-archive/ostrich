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

import java.net.InetAddress
import scala.collection.immutable
import com.twitter.json.Json
import com.twitter.xrayspecs.{Duration, Time}
import com.twitter.xrayspecs.TimeConversions._
import net.lag.logging.Logger

/**
 * Log all collected stats as a json line to a java logger at a regular interval.
 */
class JsonStatsLogger(val logger: Logger, val period: Duration, val serviceName: Option[String])
      extends PeriodicBackgroundProcess("JsonStatsLogger", period) {
  def this(logger: Logger, period: Duration) = this(logger, period, None)

  val collection = Stats.fork()
  val hostname = InetAddress.getLocalHost().getCanonicalHostName()

  def periodic() {
    val statMap =
      collection.getCounterStats(true) ++
      Stats.getGaugeStats(true) ++
      collection.getTimingStats(true).flatMap { case (key, timing) =>
        timing.toMap.map { case (subkey, value) =>
          ("timing_" + key + "_" + subkey, value)
        }
      } ++
      Stats.getJvmStats().map { case (key, value) =>
        ("jvm_" + key, value)
      } ++
      immutable.Map("service" -> serviceName.getOrElse("unknown"),
                    "source" -> hostname,
                    "timestamp" -> Time.now.inSeconds)
    val cleanedKeysStatMap = statMap.map { case (key, value) => (key.replaceAll(":", "_"), value) }

    logger.info(Json.build(immutable.Map(cleanedKeysStatMap.toSeq: _*)).toString)
  }
}
