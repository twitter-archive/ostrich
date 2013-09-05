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

import java.net.{Socket, InetAddress}
import java.io.{IOException, OutputStreamWriter}
import com.twitter.conversions.time._
import com.twitter.logging.Logger
import com.twitter.ostrich.admin.config.StatsReporterConfig
import com.twitter.ostrich.admin.{AdminHttpService, PeriodicBackgroundProcess, StatsReporterFactory}
import com.twitter.util.Duration

/**
 * Write stats to Graphite.  Both a prefix and/or a service name can be
 * provided to be appended to the front of the metric keys.
 */
@deprecated("use GraphiteStatsLoggerFactory")
class GraphiteStatsLoggerConfig extends StatsReporterConfig {
  var period: Duration = 1.minute
  var serviceName: Option[String] = None
  var prefix: String = "unknown"
  var host: String = "localhost"
  var port: Int = 2013

  def apply() = { (collection: StatsCollection, admin: AdminHttpService) =>
    new GraphiteStatsLogger(host, port, period, prefix, serviceName, collection)
  }
}

class GraphiteStatsLoggerFactory(
    period: Duration = 1.minute,
    serviceName: Option[String] = None,
    prefix: String = "unknown",
    host: String = "localhost",
    port: Int = 2013)
  extends StatsReporterFactory {

  def apply(collection: StatsCollection, admin: AdminHttpService) =
    new GraphiteStatsLogger(host, port, period, prefix, serviceName, collection)
}

/**
 * Log all collected stats to Graphite
 */
class GraphiteStatsLogger(val host: String, val port: Int, val period: Duration, val prefix: String,
                          val serviceName: Option[String], collection: StatsCollection)
  extends PeriodicBackgroundProcess("GraphiteStatsLogger", period) {
  val logger = Logger.get()

  val listener = new StatsListener(collection)
  val hostname = InetAddress.getLocalHost.getCanonicalHostName

  def periodic() {
    try {
      write(new Socket(host, port))
    } catch {
      case e: IOException => logger.error("Error connecting to graphite: %s", e.getMessage)
    }
  }

  def write(sock: Socket) {
    val stats = listener.get()
    val statMap =
      stats.counters.map { case (key, value) => (key, value.doubleValue) } ++
      stats.gauges ++
      stats.metrics.flatMap { case (key, distribution) =>
        distribution.toMap.map { case (subkey, value) =>
          (key + "_" + subkey, value.doubleValue)
        }
      }
    val cleanedKeysStatMap = statMap.map { case (key, value) =>
      (key.replaceAll(":", "_").replaceAll("/", ".").replaceAll(" ", "_").toLowerCase(), value) }

    var writer: OutputStreamWriter = null
    try {
      val epoch = System.currentTimeMillis() / 1000

      writer = new OutputStreamWriter(sock.getOutputStream)

      try {
        cleanedKeysStatMap.foreach { case (key, value) => {
          writer.write("%s.%s.%s %.2f %d\n".formatLocal(java.util.Locale.US, prefix, serviceName.getOrElse("unknown"), key, value.doubleValue,
            epoch))
        }}
      } catch {
        case e: IOException => logger.error("Error writing data to graphite: %s", e.getMessage)
      }

      writer.flush()
    } catch {
      case e: Exception =>
        logger.error("Error writing to Graphite: %s", e.getMessage)
        if (writer != null) {
          try {
            writer.flush()
          } catch {
            case ioe: IOException =>
              logger.error("Error while flushing writer: %s", ioe.getMessage)
          }
        }
    } finally {
      if (sock != null) {
        try {
          sock.close();
        } catch {
          case ioe: IOException => logger.error("Error while closing socket: %s", ioe.getMessage)
        }
      }
      writer = null
    }
  }
}
