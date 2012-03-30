/*
 * Copyright 2009 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.ostrich
package admin

import java.net.{Socket, SocketException, URI, URL}
import scala.collection.Map
import scala.collection.JavaConverters._
import scala.io.Source
import com.twitter.json.Json
import com.twitter.logging.{Level, Logger}
import org.specs.SpecificationWithJUnit
import org.specs.util.DataTables
import stats.{Stats, StatsListener}

class AdminHttpServiceSpec extends ConfiguredSpecification with DataTables {
  def get(path: String): String = {
    val port = service.address.getPort
    val url = new URL("http://localhost:%s%s".format(port, path))
    Source.fromURL(url).getLines().mkString("\n")
  }

  def getHeaders(path: String): Map[String, List[String]] = {
    val port = service.address.getPort
    val url = new URL("http://localhost:%s%s".format(port, path))
    url.openConnection().getHeaderFields.asScala.mapValues { _.asScala.toList }
  }

  var service: AdminHttpService = null

  "AdminHttpService" should {
    doBefore {
      service = new AdminHttpService(0, 20, Stats, new RuntimeEnvironment(getClass))
      service.start()
    }

    doAfter {
      Stats.clearAll()
      StatsListener.clearAll()
      service.shutdown()
    }

    "FolderResourceHandler" in {
      val staticHandler = new FolderResourceHandler("/nested")

      "split a URI" in {
        staticHandler.getRelativePath("/nested/1level.txt") mustEqual "1level.txt"
        staticHandler.getRelativePath("/nested/2level/2level.txt") mustEqual "2level/2level.txt"
      }

      "build paths correctly" in {
        staticHandler.buildPath("1level.txt") mustEqual "/nested/1level.txt"
        staticHandler.buildPath("2level/2level.txt") mustEqual "/nested/2level/2level.txt"
      }

      "load resources" in {
        staticHandler.loadResource("nested/1level.txt") must throwA[Exception]
        staticHandler.loadResource("/nested/1level.txt") mustNot throwA[Exception]
      }
    }

    "static resources" in {
      "drawgraph.js" in {
        val inputStream = getClass.getResourceAsStream("/static/drawgraph.js")
        inputStream mustNot beNull
        Source.fromInputStream(inputStream).mkString mustNot beNull
      }

      "unnested" in {
        val inputStream = getClass.getResourceAsStream("/unnested.txt")
        Source.fromInputStream(inputStream).mkString must beMatching("we are not nested")
      }

      "1 level of nesting" in {
        val inputStream = getClass.getResourceAsStream("/nested/1level.txt")
        Source.fromInputStream(inputStream).mkString must beMatching("nested one level deep")
      }

      "2 levels of nesting" in {
        val inputStream = getClass.getResourceAsStream("/nested/2levels/2levels.txt")
        Source.fromInputStream(inputStream).mkString must beMatching("nested two levels deep")
      }
    }

    "start and stop" in {
      val port = service.address.getPort
      new Socket("localhost", port) must notBeNull
      service.shutdown()
      new Socket("localhost", port) must throwA[SocketException]
    }

    "answer pings" in {
      val port = service.address.getPort
      val socket = new Socket("localhost", port)
      get("/ping.json").trim mustEqual """{"response":"pong"}"""

      service.shutdown()
      new Socket("localhost", port) must eventually(throwA[SocketException])
    }

    "shutdown" in {
      val port = service.address.getPort
      get("/shutdown.json")
      new Socket("localhost", port) must eventually(throwA[SocketException])
    }

    "quiesce" in {
      val port = service.address.getPort
      get("/quiesce.json")
      new Socket("localhost", port) must eventually(throwA[SocketException])
    }

    "get a proper web page back for the report URL" in {
      get("/report/") must beMatching("Stats Report")
    }

    "return 404 for favicon" in {
      get("/favicon.ico") must throwA[java.io.FileNotFoundException]
    }

    "return 404 for a missing command" in {
      get("/bullshit.json") must throwA[java.io.FileNotFoundException]
    }

    "not crash when fetching /" in {
      get("/") must beMatching("ostrich")
    }

    "tell us its ostrich version in the headers" in {
      getHeaders("/").get("X-ostrich-version") must beSome[List[String]]
    }

    "server info" in {
      val serverInfo = get("/server_info.json")
      serverInfo mustMatch("\"build\":")
      serverInfo mustMatch("\"build_revision\":")
      serverInfo mustMatch("\"name\":")
      serverInfo mustMatch("\"version\":")
      serverInfo mustMatch("\"start_time\":")
      serverInfo mustMatch("\"uptime\":")
    }

    "fetch static files" in {
      get("/static/drawgraph.js") must include("drawChart")
    }

    "provide stats" in {
      doAfter {
        service.shutdown()
      }

      "in json" in {
        // make some statsy things happen
        Stats.clearAll()
        Stats.time("kangaroo_time") { Stats.incr("kangaroos", 1) }

        val stats = Json.parse(get("/stats.json")).asInstanceOf[Map[String, Map[String, AnyRef]]]
        stats("gauges") must haveKey("jvm_uptime")
        stats("gauges") must haveKey("jvm_heap_used")
        stats("counters") must haveKey("kangaroos")
        stats("metrics") must haveKey("kangaroo_time_msec")

        val timing = stats("metrics")("kangaroo_time_msec").asInstanceOf[Map[String, Int]]
        timing("count") mustEqual 1
        timing("minimum") must be_>=(0)
        timing("maximum") must be_>=(timing("minimum"))
      }

      "in json, with custom listeners" in {
        Stats.clearAll()
        Stats.incr("apples", 10)
        Stats.addMetric("oranges", 5)

        var absStats = Json.parse(get("/stats.json")).asInstanceOf[Map[String, Map[String, AnyRef]]]
        absStats("counters")("apples") mustEqual 10
        absStats("metrics")("oranges").asInstanceOf[Map[String, AnyRef]]("count") mustEqual 1
        var namespaceStats = Json.parse(get("/stats.json?namespace=monkey"))
                                 .asInstanceOf[Map[String, Map[String, AnyRef]]]
        namespaceStats("counters")("apples") mustEqual 10
        namespaceStats("metrics")("oranges").asInstanceOf[Map[String, AnyRef]]("count") mustEqual 1
        var periodicStats = Json.parse(get("/stats.json?period=30"))
                                .asInstanceOf[Map[String, Map[String, AnyRef]]]
        periodicStats("counters")("apples") mustEqual 10
        periodicStats("metrics")("oranges").asInstanceOf[Map[String, AnyRef]]("count") mustEqual 1

        Stats.incr("apples", 6)
        Stats.addMetric("oranges", 3)
        absStats = Json.parse(get("/stats.json")).asInstanceOf[Map[String, Map[String, AnyRef]]]
        absStats("counters")("apples") mustEqual 16
        absStats("metrics")("oranges").asInstanceOf[Map[String, AnyRef]]("count") mustEqual 2
        namespaceStats = Json.parse(get("/stats.json?namespace=monkey"))
                             .asInstanceOf[Map[String, Map[String, AnyRef]]]
        namespaceStats("counters")("apples") mustEqual 6
        namespaceStats("metrics")("oranges").asInstanceOf[Map[String, AnyRef]]("count") mustEqual 1
        periodicStats = Json.parse(get("/stats.json?period=30"))
                            .asInstanceOf[Map[String, Map[String, AnyRef]]]
        periodicStats("counters")("apples") mustEqual 10
        periodicStats("metrics")("oranges").asInstanceOf[Map[String, AnyRef]]("count") mustEqual 1

        namespaceStats = Json.parse(get("/stats.json?namespace=monkey"))
                             .asInstanceOf[Map[String, Map[String, AnyRef]]]
        namespaceStats("counters")("apples") mustEqual 0
        namespaceStats("metrics")("oranges").asInstanceOf[Map[String, AnyRef]]("count") mustEqual 0
      }

      "in json, with histograms" in {
        // make some statsy things happen
        Stats.clearAll()
        get("/stats.json")
        Stats.addMetric("kangaroo_time", 1)
        Stats.addMetric("kangaroo_time", 2)
        Stats.addMetric("kangaroo_time", 3)
        Stats.addMetric("kangaroo_time", 4)
        Stats.addMetric("kangaroo_time", 5)
        Stats.addMetric("kangaroo_time", 6)

        val stats = get("/stats.json?period=30")
        val json = Json.parse(stats).asInstanceOf[Map[String, Map[String, AnyRef]]]
        val timings = json("metrics")("kangaroo_time").asInstanceOf[Map[String, Int]]

        timings must haveKey("count")
        timings("count") mustEqual 6

        timings must haveKey("average")
        timings("average") mustEqual 3

        timings must haveKey("p25")
        timings("p25") mustEqual 2

        timings must haveKey("p50")
        timings("p50") mustEqual 3

        timings must haveKey("p75")
        timings("p75")  mustEqual 5

        timings must haveKey("p99")
        timings("p99") mustEqual 6

        timings must haveKey("p999")
        timings("p999") mustEqual 6

        timings must haveKey("p9999")
        timings("p9999") mustEqual 6
      }

      "in json, with histograms and reset" in {
        Stats.clearAll()
        // Add items indirectly to the histogram
        Stats.addMetric("kangaroo_time", 1)
        Stats.addMetric("kangaroo_time", 2)
        Stats.addMetric("kangaroo_time", 3)
        Stats.addMetric("kangaroo_time", 4)
        Stats.addMetric("kangaroo_time", 5)
        Stats.addMetric("kangaroo_time", 6)


        val stats = get("/stats.json?reset")
        val json = Json.parse(stats).asInstanceOf[Map[String, Map[String, AnyRef]]]
        val timings = json("metrics")("kangaroo_time").asInstanceOf[Map[String, Int]]

        timings must haveKey("count")
        timings("count") mustEqual 6

        timings must haveKey("average")
        timings("average") mustEqual 3

        timings must haveKey("p25")
        timings("p25") mustEqual 2

        timings must haveKey("p50")
        timings("p50") mustEqual 3

        timings must haveKey("p75")
        timings("p75")  mustEqual 5

        timings must haveKey("p95")
        timings("p95") mustEqual 6

        timings must haveKey("p99")
        timings("p99") mustEqual 6

        timings must haveKey("p999")
        timings("p999") mustEqual 6

        timings must haveKey("p9999")
        timings("p9999") mustEqual 6
      }

      "in json, with callback" in {
        val stats = get("/stats.json?callback=true")
        stats.startsWith("ostrichCallback(") mustBe true
        stats.endsWith(")") mustBe true
      }

      "in json, with named callback" in {
        val stats = get("/stats.json?callback=My.Awesome.Callback")
        stats.startsWith("My.Awesome.Callback(") mustBe true
        stats.endsWith(")") mustBe true
      }

      "in json, with empty callback" in {
        val stats = get("/stats.json?callback=")
        stats.startsWith("ostrichCallback(") mustBe true
        stats.endsWith(")") mustBe true
      }

      "in text" in {
        // make some statsy things happen
        Stats.clearAll()
        Stats.time("kangaroo_time") { Stats.incr("kangaroos", 1) }

        get("/stats.txt") must beMatching("  kangaroos: 1")
      }
    }

    "parse parameters" in {
      "uri"         | "result"                        |>
      "/p"          ! Nil                             |
      "/p?a=b"      ! ("a", "b") :: Nil               |
      "/p?a=b&c=d"  ! ("a", "b") :: ("c", "d") :: Nil |
      "/p?"         ! Nil                             |
      "/p?invalid"  ! Nil                             |
      "/p?a="       ! ("a", "") :: Nil                |
      "/p?=b"       ! ("", "b") :: Nil                | { (uriStr, result) =>
        CgiRequestHandler.uriToParameters(new URI(uriStr)) mustEqual result
      }
    }
  }
}
