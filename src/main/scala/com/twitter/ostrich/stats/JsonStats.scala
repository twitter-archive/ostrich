package com.twitter.ostrich
package stats

import com.codahale.jerkson.Json.generate
import com.twitter.logging.Logger
import scala.collection.mutable

class JsonStats(logger: Logger) extends TransactionalStatsCollection {
  def write(summary: StatsSummary) {
    val map = flatten(summary)
    val json = generate(map)
    logger.info(json)
  }

  private def flatten(summary: StatsSummary) = {
    val rv = new mutable.HashMap[String, Any]
    rv ++= summary.counters
    rv ++= summary.metrics.map { case (k1, d) => (k1, d.mean.toInt) }
    rv ++= summary.gauges
    rv ++= summary.labels
    rv.toMap
  }
}
