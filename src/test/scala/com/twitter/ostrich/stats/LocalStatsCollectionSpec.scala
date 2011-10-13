package com.twitter.ostrich.stats

import com.twitter.util.Future
import org.specs.Specification

class LocalStatsCollectionSpec extends Specification {
  val jobClassName = "rooster.TestCapturer"

  "LocalStatsCollection" should {
    val localStats = LocalStatsCollection(jobClassName)

    doAfter {
      Stats.clearAll()
    }

    "writes to global stats at the same time" in {
      localStats.addMetric("whateva", 5)
      localStats.addMetric("whateva", 15)
      Stats.getMetric("whateva")() mustEqual Distribution(Histogram(5, 15))
      localStats.getMetric("whateva")() mustEqual Distribution(Histogram(5, 15))
    }

    "flush" in {
      localStats.incr("tflock")
      localStats.incr("tflock")
      localStats.getCounter("tflock")() mustEqual 2
      localStats.addMetric("timing", 900)
      localStats.getMetric("timing")() mustEqual Distribution(Histogram(900))

      localStats.flushInto(Stats)

      Stats.getCounter(jobClassName + ".tflock")() mustEqual 2
      Stats.getMetric(jobClassName + ".timing")() mustEqual Distribution(Histogram(900))
      localStats.getCounter("tflock")() mustEqual 0
      localStats.getMetric("timing")() mustEqual Distribution(Histogram())
    }
  }
}
