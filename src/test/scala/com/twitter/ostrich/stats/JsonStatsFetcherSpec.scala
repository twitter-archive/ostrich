package com.twitter.ostrich.stats

import scala.io.Source
import com.twitter.io.TempFile
import com.twitter.ostrich.admin._
import org.specs.SpecificationWithJUnit
import org.specs.util.TimeConversions._

class JsonStatsFetcherSpec extends SpecificationWithJUnit {
  def exec(args: String*) = Runtime.getRuntime.exec(args.toArray)

  val hasRuby = try {
    exec("ruby", "--version")
    false // <-- change back to true when this works on hudson
  } catch {
    case e: Throwable => false
  }

  if (hasRuby) {
    "json_stats_fetcher.rb" should {
      var service: AdminHttpService = null
      val script = TempFile.fromResourcePath("/json_stats_fetcher.rb").getAbsolutePath

      doBefore {
        exec("chmod", "+x", script)
        Stats.clearAll()
        StatsListener.clearAll()
        service = new AdminHttpService(0, 20, Stats, new RuntimeEnvironment(getClass))
        service.start()
      }

      doAfter {
        service.shutdown()
        Stats.clearAll()
      }

      def getStats = {
        val process = exec(script, "-w", "-t", "1", "-p", service.address.getPort.toString, "-n")
        process.waitFor()
        Source.fromInputStream(process.getInputStream).mkString.split("\n")
      }

      "fetch a stat" in {
        Stats.incr("bugs")
        getStats must contain("bugs=1")
        Stats.incr("bugs", 37)
        getStats must contain("bugs=37").eventually(3,  500.milliseconds)
      }
    }
  }
}
