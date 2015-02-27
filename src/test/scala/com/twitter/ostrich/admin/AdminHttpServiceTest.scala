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

package com.twitter.ostrich.admin

import com.twitter.conversions.time._
import com.twitter.json.Json
import com.twitter.logging.{Level, Logger}
import com.twitter.ostrich.stats.{Stats, StatsListener}
import java.net.{Socket, SocketException, URI, URL}
import java.util.regex.Pattern
import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.TableDrivenPropertyChecks
import scala.collection.JavaConverters._
import scala.io.Source

@RunWith(classOf[JUnitRunner])
class AdminHttpServiceTest extends FunSuite with BeforeAndAfter
  with TableDrivenPropertyChecks
  with Eventually
  with IntegrationPatience {

  class Context {}

  def get(path: String): String = {
    val port = service.address.getPort
    val url = new URL(f"http://localhost:$port%d$path%s")
    Source.fromURL(url).getLines().mkString("\n")
  }

  def getHeaders(path: String): Map[String, List[String]] = {
    val port = service.address.getPort
    val url = new URL(f"http://localhost:$port%d$path%s")
    url.openConnection().getHeaderFields.asScala.toMap.mapValues { _.asScala.toList }
  }

  var service: AdminHttpService = null

  before {
    service =
      new AdminHttpService(
        0,
        20,
        Stats,
        new RuntimeEnvironment(getClass),
        30.seconds,
        { code => /* system-exit is a noop here */ }
      )
    service.start()
  }

  after {
    Stats.clearAll()
    StatsListener.clearAll()
    service.shutdown()
  }

  test("FolderResourceHandler") {
    val staticHandler = new FolderResourceHandler("/nested")

    info("split a URI")
    assert(staticHandler.getRelativePath("/nested/1level.txt") === "1level.txt")
    assert(staticHandler.getRelativePath("/nested/2level/2level.txt") === "2level/2level.txt")


    info("build paths correctly")
    assert(staticHandler.buildPath("1level.txt") === "/nested/1level.txt")
    assert(staticHandler.buildPath("2level/2level.txt") === "/nested/2level/2level.txt")

    info("load resources")
    intercept[Exception] { staticHandler.loadResource("nested/1level.txt") }
    try {
      staticHandler.loadResource("/nested/1level.txt")
    } catch {
      case e: Exception => fail("staticHandler should not throw an exception")
    }
  }

  test("static resources") {
    new Context {
      info("drawgraph.js")
      val inputStream = getClass.getResourceAsStream("/static/drawgraph.js")
      assert(inputStream !== null)
      assert(Source.fromInputStream(inputStream).mkString !== null)
    }

    new Context {
      info("unnested")
      val inputStream = getClass.getResourceAsStream("/unnested.txt")
      assert(Pattern.matches("we are not nested", Source.fromInputStream(inputStream).getLines.mkString))
    }

    new Context {
      info("1 level of nesting")
      val inputStream = getClass.getResourceAsStream("/nested/1level.txt")
      assert(Pattern.matches("nested one level deep", Source.fromInputStream(inputStream).getLines.mkString))
    }

    new Context {
      info("2 levels of nesting")
      val inputStream = getClass.getResourceAsStream("/nested/2levels/2levels.txt")
      assert(Pattern.matches("nested two levels deep", Source.fromInputStream(inputStream).getLines.mkString))
    }
  }

  test("start and stop") {
    val port = service.address.getPort
    val socket = new Socket("localhost", port)
    socket.close()
    service.shutdown()
    eventually {
      intercept[SocketException] { new Socket("localhost", port) }
    }
  }

  test("answer pings") {
    val port = service.address.getPort
    assert(get("/ping.json").trim === """{"response":"pong"}""")
    service.shutdown()
    eventually {
      intercept[SocketException] { new Socket("localhost", port) }
    }
  }

  test("shutdown") {
    val port = service.address.getPort
    get("/shutdown.json")
    eventually {
      intercept[SocketException] { new Socket("localhost", port) }
    }
  }

  test("quiesce") {
    val port = service.address.getPort
    get("/quiesce.json")
    eventually {
      intercept[SocketException] { new Socket("localhost", port) }
    }
  }

  test("get a proper web page back for the report URL") {
    assert(get("/report/").contains("Stats Report"))
  }

  test("return 404 for favicon") {
    intercept[java.io.FileNotFoundException] { get("/favicon.ico") }
  }

  test("return 404 for a missing command") {
    intercept[java.io.FileNotFoundException] { get("/bullshit.json") }
  }

  test("not crash when fetching /") {
    assert(get("/").contains("ostrich"))
  }

  test("tell us its ostrich version in the headers") {
    assert(getHeaders("/").get("X-ostrich-version").isInstanceOf[Some[List[String]]])
  }

  test("server info") {
    val serverInfo = get("/server_info.json")
    assert(serverInfo.contains("\"build\":"))
    assert(serverInfo.contains("\"build_revision\":"))
    assert(serverInfo.contains("\"name\":"))
    assert(serverInfo.contains("\"version\":"))
    assert(serverInfo.contains("\"start_time\":"))
    assert(serverInfo.contains("\"uptime\":"))
  }

  test("change log levels") {
    // Add a logger with a very specific name
    val name = "logger-" + System.currentTimeMillis
    val logger = Logger.get(name) // register this logger
    logger.setLevel(Level.INFO)

    // no levels specified
    var logLevels = get("/logging")
    assert(logLevels.contains(name))
    assert(logLevels.contains("Specify a logger name and level"))

    // specified properly
    logLevels = get("/logging?name=%s&level=FATAL".format(name))
    assert(Logger.get(name).getLevel() === Level.FATAL)
    assert(logLevels.contains("Successfully changed the level of the following logger"))

    // made up level
    logLevels = get("/logging?name=%s&level=OHEMGEE".format(name))
    assert(logLevels.contains("Logging level change failed"))

    // made up logger
    logLevels = get("/logging?name=OHEMGEEWHYAREYOUUSINGTHISLOGGERNAME&level=INFO")
    assert(logLevels.contains("Logging level change failed"))
  }

  test("fetch static files") {
    assert(get("/static/drawgraph.js").contains("drawChart"))
  }

  test("mesos health") {
    assert(get("/health").contains("OK"))
  }

  test("mesos abortabortabort") {
    val port = service.address.getPort
    get("/abortabortabort")
    eventually {
      intercept[SocketException] { new Socket("localhost", port) }
    }
  }

  test("mesos quitquitquit") {
    val port = service.address.getPort
    get("/quitquitquit")
    eventually {
      intercept[SocketException] { new Socket("localhost", port) }
    }
  }

  test("thread contention") {
    val prof = get("/contention.json")
    assert(prof.contains("\"blocked_threads\":"))
  }

  test("provide stats") {
    new Context {
      info("in json")
      // make some statsy things happen
      Stats.clearAll()
      Stats.time("kangaroo_time") { Stats.incr("kangaroos", 1) }

      val stats = Json.parse(get("/stats.json")).asInstanceOf[Map[String, Map[String, AnyRef]]]
      assert(stats("gauges").get("jvm_uptime").isDefined)
      assert(stats("gauges").get("jvm_heap_used").isDefined)
      assert(stats("counters").get("kangaroos").isDefined)
      assert(stats("metrics").get("kangaroo_time_msec").isDefined)

      val timing = stats("metrics")("kangaroo_time_msec").asInstanceOf[Map[String, Int]]
      assert(timing("count") === 1)
      assert(timing("minimum") >= 0)
      assert(timing("maximum") >= timing("minimum"))
    }

    new Context {
      info("in json, with custom listeners")
      Stats.clearAll()
      Stats.incr("apples", 10)
      Stats.addMetric("oranges", 5)

      var absStats = Json.parse(get("/stats.json")).asInstanceOf[Map[String, Map[String, AnyRef]]]
      assert(absStats("counters")("apples") === 10)
      assert(absStats("metrics")("oranges").asInstanceOf[Map[String, AnyRef]]("count") === 1)
      var namespaceStats = Json.parse(get("/stats.json?namespace=monkey"))
        .asInstanceOf[Map[String, Map[String, AnyRef]]]
      assert(namespaceStats("counters")("apples") === 10)
      assert(namespaceStats("metrics")("oranges").asInstanceOf[Map[String, AnyRef]]("count") === 1)
      var periodicStats = Json.parse(get("/stats.json?period=30"))
        .asInstanceOf[Map[String, Map[String, AnyRef]]]
      assert(periodicStats("counters")("apples") === 10)
      assert(periodicStats("metrics")("oranges").asInstanceOf[Map[String, AnyRef]]("count") === 1)

      Stats.incr("apples", 6)
      Stats.addMetric("oranges", 3)
      absStats = Json.parse(get("/stats.json")).asInstanceOf[Map[String, Map[String, AnyRef]]]
      assert(absStats("counters")("apples") === 16)
      assert(absStats("metrics")("oranges").asInstanceOf[Map[String, AnyRef]]("count") === 2)
      namespaceStats = Json.parse(get("/stats.json?namespace=monkey"))
        .asInstanceOf[Map[String, Map[String, AnyRef]]]
      assert(namespaceStats("counters")("apples") === 6)
      assert(namespaceStats("metrics")("oranges").asInstanceOf[Map[String, AnyRef]]("count") === 1)
      namespaceStats = Json.parse(get("/stats.json?namespace=monkey"))
        .asInstanceOf[Map[String, Map[String, AnyRef]]]
      assert(namespaceStats("counters")("apples") === 0)
      assert(namespaceStats("metrics")("oranges").asInstanceOf[Map[String, AnyRef]]("count") === 0)
      periodicStats = Json.parse(get("/stats.json?period=30"))
        .asInstanceOf[Map[String, Map[String, AnyRef]]]
      if (periodicStats("counters")("apples") == 6) {
        // PeriodicBackgroundProcess aligns the first event to the multiple
        // of the period + 1 so the first event can happen as soon as in two
        // seconds. In the case of the first event already happens when we
        // check the stats, we retry the test.
        Stats.incr("apples", 8)
        Stats.addMetric("oranges", 4)
        periodicStats = Json.parse(get("/stats.json?period=30"))
          .asInstanceOf[Map[String, Map[String, AnyRef]]]
        assert(periodicStats("counters")("apples") === 6)
        assert(periodicStats("metrics")("oranges").asInstanceOf[Map[String, AnyRef]]("count") === 1)
      } else {
        assert(periodicStats("counters")("apples") === 10)
        assert(periodicStats("metrics")("oranges").asInstanceOf[Map[String, AnyRef]]("count") === 1)
      }
    }

    new Context {
      info("in json, with histograms")
      // make some statsy things happen
      Stats.clearAll()
      get("/stats.json")
      Stats.addMetric("kangaroo_time", 1)
      Stats.addMetric("kangaroo_time", 2)
      Stats.addMetric("kangaroo_time", 3)
      Stats.addMetric("kangaroo_time", 4)
      Stats.addMetric("kangaroo_time", 5)
      Stats.addMetric("kangaroo_time", 6)

      val stats = get("/stats.json")
      val json = Json.parse(stats).asInstanceOf[Map[String, Map[String, AnyRef]]]
      val timings = json("metrics")("kangaroo_time").asInstanceOf[Map[String, Int]]

      assert(timings.get("count").isDefined)
      assert(timings("count") === 6)

      assert(timings.get("average").isDefined)
      assert(timings("average") === 3)

      assert(timings.get("p50").isDefined)
      assert(timings("p50") === 3)

      assert(timings.get("p99").isDefined)
      assert(timings("p99") === 6)

      assert(timings.get("p999").isDefined)
      assert(timings("p999") === 6)

      assert(timings.get("p9999").isDefined)
      assert(timings("p9999") === 6)
    }

    new Context {
      info("in json, with histograms and reset")
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

      assert(timings.get("count").isDefined)
      assert(timings("count") === 6)

      assert(timings.get("average").isDefined)
      assert(timings("average") === 3)

      assert(timings.get("p50").isDefined)
      assert(timings("p50") === 3)

      assert(timings.get("p95").isDefined)
      assert(timings("p95") === 6)

      assert(timings.get("p99").isDefined)
      assert(timings("p99") === 6)

      assert(timings.get("p999").isDefined)
      assert(timings("p999") === 6)

      assert(timings.get("p9999").isDefined)
      assert(timings("p9999") === 6)
    }


  new Context {
    info("in json, with callback")
      val stats = get("/stats.json?callback=true")
      assert(stats.startsWith("ostrichCallback("))
      assert(stats.endsWith(")"))
    }

  new Context {
    info("in json, with named callback")
      val stats = get("/stats.json?callback=My.Awesome.Callback")
      assert(stats.startsWith("My.Awesome.Callback("))
      assert(stats.endsWith(")"))
    }

  new Context {
    info("in json, with empty callback")
      val stats = get("/stats.json?callback=")
      assert(stats.startsWith("ostrichCallback("))
      assert(stats.endsWith(")"))
    }

  new Context {
    info("in text")
      // make some statsy things happen
      Stats.clearAll()
      Stats.time("kangaroo_time") { Stats.incr("kangaroos", 1) }

      assert(get("/stats.txt").contains("  kangaroos: 1"))
    }
  }

  test("return 400 for /stats when collection period is below minimum") {
    intercept[Exception] { get("/stats.json?period=10") }
  }

  test("parse parameters") {
    val parametersTable =
      Table(
        ("uri",        "result"),
        ("/p",         Nil),
        ("/p?a=b",     ("a", "b") :: Nil),
        ("/p?a=b&c=d", ("a", "b") :: ("c", "d") :: Nil),
        ("/p?",        Nil),
        ("/p?invalid", Nil),
        ("/p?a=",      ("a", "") :: Nil),
        ("/p?=b",      ("", "b") :: Nil)
      )

    forAll (parametersTable) { (uriStr: String, result: List[(String, String)]) =>
      assert(CgiRequestHandler.uriToParameters(new URI(uriStr)) === result)
    }
  }

}
