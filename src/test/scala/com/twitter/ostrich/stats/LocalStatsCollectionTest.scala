package com.twitter.ostrich.stats

import com.twitter.util.Future
import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LocalStatsCollectionTest extends FunSuite with BeforeAndAfter {

  class Context {
    val jobClassName = "rooster.TestCapturer"
    val localStats = LocalStatsCollection(jobClassName)
  }

  after {
    Stats.clearAll()
  }

  test("writes to global stats at the same time") {
    val context = new Context
    import context._

    localStats.addMetric("whateva", 5)
    localStats.addMetric("whateva", 15)
    assert(Stats.getMetric("whateva")() === Distribution(Histogram(5, 15)))
    assert(localStats.getMetric("whateva")() === Distribution(Histogram(5, 15)))
  }

  test("flush") {
    val context = new Context
    import context._

    localStats.incr("tflock")
    localStats.incr("tflock")
    assert(localStats.getCounter("tflock")() === 2)
    localStats.addMetric("timing", 900)
    assert(localStats.getMetric("timing")() === Distribution(Histogram(900)))

    localStats.flushInto(Stats)

    assert(Stats.getCounter(jobClassName + ".tflock")() === 2)
    assert(Stats.getMetric(jobClassName + ".timing")() === Distribution(Histogram(900)))
    assert(localStats.getCounter("tflock")() === 0)
    assert(localStats.getMetric("timing")() === Distribution(Histogram()))
  }

}
