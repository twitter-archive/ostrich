package com.twitter.ostrich.stats

import com.twitter.util.Future
import org.specs.Specification

class BoundedStatsSpec extends Specification {
  val jobClassName = "rooster.TestCapturer"

  "BoundedStats" should {
    val localStats = LocalStatsCollection(jobClassName)

    doAfter {
      Stats.clearAll()
    }

    "writes to global stats at the same time" in {
      localStats.addMetric("whateva", 5)
      localStats.addMetric("whateva", 15)
      Stats.getMetric("whateva").apply(false) mustEqual localStats.getMetric("whateva").apply(false)
    }

    "flush" in {
      localStats.incr("tflock")
      localStats.incr("tflock")
      localStats.getCounter("tflock")() mustEqual 2

      localStats.flushInto(Stats)

      Stats.getCounter(jobClassName + ".tflock")() mustEqual 2
      localStats.getCounter("tflock")() mustEqual 0
    }
  }
}
