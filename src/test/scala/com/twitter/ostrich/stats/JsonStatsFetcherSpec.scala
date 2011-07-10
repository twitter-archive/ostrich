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
      val script = "./src/scripts/json_stats_fetcher.rb"

      doBefore {
        Stats.clearAll()
        service = new AdminHttpService(0, 20, new RuntimeEnvironment(getClass))
        service.start()
      }

      doAfter {
        service.shutdown()
        Stats.clearAll()
      }

      def getStats = {
        val process = exec(script, "-w", "-p", service.address.getPort.toString, "-n")
        process.waitFor()
        Source.fromInputStream(process.getInputStream).mkString.split("\n")
      }

      "fetch a stat" in {
        Stats.incr("bugs")
        import org.specs.util.TimeConversions._
        getStats must contain("bugs=1").eventually(60, 1.second)
      }
    }
  }
}
