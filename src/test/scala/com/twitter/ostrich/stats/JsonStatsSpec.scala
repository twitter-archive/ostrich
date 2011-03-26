package com.twitter.ostrich
package stats

import com.codahale.jerkson.Json._
import com.twitter.logging.{BareFormatter, Level, Logger, StringHandler}
import org.specs.Specification

object JsonStatsSpec extends Specification {
  "JSON Stats" should {
    Logger.reset()

    val logger = Logger.get("json")
    logger.setLevel(Level.INFO)
    val handler = new StringHandler(BareFormatter, None)
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)

    val json = new JsonStats(logger)

    def getLine() = {
      val rv = handler.get.split("\n").head
      handler.clear()
      rv
    }

    doBefore {
      Logger.get("").setLevel(Level.OFF)
      Stats.clearAll()
      handler.clear()
    }

    "can be called manually" in {
      val counters = Map("widgets" -> 3L)
      val gauges = Map("wodgets" -> 3.5)
      val metrics = Map("timing_msec" -> new Distribution(1, 10, 10, 10))
      val labels = Map("this" -> "that")
      json.write(StatsSummary(counters, metrics, gauges, labels))
      getLine() mustEqual """{"timing_msec":10,"wodgets":3.5,"widgets":3,"this":"that"}"""
    }

    "can be called transactionally" in {
      json { stats =>
        stats.setLabel("test", "blah")
        stats.setLabel("test2", "crap")

        val response: Int = stats.time[Int]("test-time") {
          1+1
        }

        response mustEqual 2
      }

      val line = getLine()
      val map = parse[Map[String, AnyRef]](line)
      map("test") mustEqual "blah"
      map("test2") mustEqual "crap"
      map("test-time_msec").asInstanceOf[Int] must be_>=(0)
    }

    "sum counts within a transaction" in {
      json { stats =>
        stats.incr("test", 8)
        stats.incr("test", 8)
      }
      getLine() mustEqual """{"test":16}"""
    }
  }
}
