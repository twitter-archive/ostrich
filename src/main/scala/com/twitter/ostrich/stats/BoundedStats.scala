package com.twitter.ostrich.stats

import com.twitter.util.Local
import scala.collection.mutable.Map
import scala.collection.JavaConversions._
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object BoundedStats {
  val Zero = new AtomicInteger()

  private[this] val statPrefix = new Local[String]
  private[this] val counter = new Local[ConcurrentHashMap[String, AtomicInteger]]

  def startCapture(name: String): Unit = {
    counter.update(new ConcurrentHashMap[String, AtomicInteger]())
    statPrefix.update(name)
  }

  def isCapturing: Boolean = counter().isDefined

  def flush: Unit = {
    if (isCapturing) {
      counter().map { counts =>
        counts.foreach { case (stat, count) =>
          val count = Option(counter().get.get(stat)).getOrElse(Zero).get
          getMetric(stat).add(count)
        }
      }
    }
    counter.clear
    statPrefix.clear
  }

  def incr(statName: String, count: Int = 1) {
    Stats.incr(statName, count)
    if ( isCapturing ) {
      counter().get.putIfAbsent(statName, new AtomicInteger(0))
      counter().get.get(statName).addAndGet(count)
    }
  }

  def getMetric(statName: String) = {
    Stats.getMetric( statPrefix().get + "." + statName )
  }
}
