package com.twitter.ostrich.stress

import com.twitter.ostrich.W3CStats
import net.lag.logging.{Formatter, Level, Logger, StringHandler}
import java.text.SimpleDateFormat
import java.util.Date

object W3CStresser {
  /**
   * Generates 100k w3c lines, each being 1k columns wide, writes them to an in-memory StringHandler
   * so we're only CPU bound.
   */
  def main(args: Array[String]) {
    val logger = Logger.get("w3c")
    logger.setLevel(Level.INFO)
    val formatter = new Formatter {
      override def lineTerminator = ""
      override def dateFormat = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS")
      override def formatPrefix(level: java.util.logging.Level, date: String, name: String) = name + ": "
    }
    val handler = new StringHandler(formatter)
    logger.addHandler(handler)
    logger.setUseParentHandlers(false)

    val thousandInts = (1 until 1000).toArray
    val thousandColumns = thousandInts.map { x: Int => x.toString }
    val hundredThousand = (1 until 100000).toArray
    val w3c = new W3CStats(logger, thousandColumns)

    println("%s Starting to stress our W3C deals".format(new Date()))
    hundredThousand.foreach { i =>
      if (i % 10000 == 0) { println("%s finished our %d'th run".format(new Date(), i)) }
      w3c.transaction {
        thousandInts.foreach { j => w3c.addTiming(j.toString, j) }
      }
      handler.clear() // based on our testing, this does not add substantial CPU pressure but reduces memory needs for the test.
    }
    println("%s Done stressing".format(new Date()))
  }
}
