package com.twitter.ostrich.stats

import scala.io.Source
import com.twitter.json.Json
import com.twitter.ostrich.admin._
import org.specs.Specification

object JsonStatsFetcherSpec extends Specification {
  def exec(args: String*) = Runtime.getRuntime.exec(args.toArray)

  val hasRuby = try {
    exec("ruby", "--version")
    true
  } catch {
    case e: Throwable => false
  }

  if (hasRuby) {
    "json_stats_fetcher.rb" should {
      var service: AdminHttpService = null

      doBefore {
        Stats.clearAll()
        service = new AdminHttpService(0, 20, new RuntimeEnvironment(getClass))
        service.start()
      }

      doAfter {
        service.shutdown()
        Stats.clearAll()
      }

      "work" in {
        val port = service.address.getPort
        Stats.incr("bugs")
        val process = exec("./src/scripts/json_stats_fetcher.rb", "-w", "-p", port.toString, "-n")
        process.waitFor()
        val lines = Source.fromInputStream(process.getInputStream).mkString.split("\n")
        lines must contain("bugs=1")
      }
    }
  }
}
