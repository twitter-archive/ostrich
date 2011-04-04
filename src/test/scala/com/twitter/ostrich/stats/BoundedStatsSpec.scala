package com.twitter.ostrich.stats

import com.twitter.util.Future
import org.specs.Specification

class BoundedStatsSpec extends Specification {
  val jobClassName = "rooster.TestCapturer"
  BoundedStats.startCapture(jobClassName)
  Stats.clearAll()

  "getMetric" should {
    "return a metric" in {
      BoundedStats.getMetric("whateva") mustEqual Stats.getMetric(jobClassName + ".whateva")
    }
  }

  "flush" should {
    "send metrics to Stats" in {
      BoundedStats.incr("tflock")
      BoundedStats.incr("tflock")
      BoundedStats.flush
      Stats.getMetric(jobClassName + ".tflock")(true).average mustEqual 2
    }
  }
}
