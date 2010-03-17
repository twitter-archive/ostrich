package com.twitter.ostrich

import java.io.{DataInputStream, InputStream}
import java.net.{Socket, SocketException}
import com.twitter.json.Json
import com.twitter.xrayspecs.Eventually
import com.twitter.xrayspecs.Time
import com.twitter.xrayspecs.TimeConversions._
import net.lag.extensions._
import net.lag.configgy.{Config, RuntimeEnvironment}
import org.mockito.Matchers._
import org.specs.Specification
import org.specs.mock.Mockito


object AdminSocketServiceSpec extends Specification with Eventually with Mockito {
  val PORT = 9995
  val config = Config.fromMap(Map("admin_text_port" -> PORT.toString))

  class PimpedInputStream(stream: InputStream) {
    def readString(maxBytes: Int) = {
      val buffer = new Array[Byte](maxBytes)
      val len = stream.read(buffer)
      new String(buffer, 0, len, "UTF-8")
    }
  }

  implicit def pimpInputStream(stream: InputStream) = new PimpedInputStream(stream)

  "AdminSocketService" should {
    var service: AdminSocketService = null

    doBefore {
      Stats.clearAll()
      new Socket("localhost", PORT) must throwA[SocketException]
      service = spy(new AdminSocketService(config, new RuntimeEnvironment(getClass)))
      service.start()
    }

    doAfter {
      service.shutdown()
    }

    "start and stop" in {
      new Socket("localhost", PORT) must notBeNull
      service.shutdown()
      new Socket("localhost", PORT) must eventually(throwA[SocketException])
      service.shutdown() was called.atLeastOnce
    }

    "answer pings" in {
      val socket = new Socket("localhost", PORT)
      socket.getOutputStream().write("ping\n".getBytes)
      socket.getInputStream().readString(1024) mustEqual "pong\n\n"
      service.shutdown()
      new Socket("localhost", PORT) must eventually(throwA[SocketException])
      service.shutdown() was called.atLeastOnce
    }

    "shutdown" in {
      val socket = new Socket("localhost", PORT)
      socket.getOutputStream().write("shutdown\n".getBytes)
      new Socket("localhost", PORT) must eventually(2, 5.seconds)(throwA[SocketException])
      service.shutdown() was called.atLeastOnce
    }

    "quiesce" in {
      val socket = new Socket("localhost", PORT)
      socket.getOutputStream().write("quiesce\n".getBytes)
      new Socket("localhost", PORT) must eventually(2, 5.seconds)(throwA[SocketException])
      service.quiesce() was called.atLeastOnce
      service.shutdown() was called.atLeastOnce
    }

    "dump thread stacks" in {
      val socket = new Socket("localhost", PORT)
      socket.getOutputStream().write("threads\n".getBytes)
      val lines = socket.getInputStream().readString(4096).split("\n")
      lines must contain("threads:")
      lines must contain("    daemon: false")
      lines must contain("    stack:")
    }

    "provide stats" in {
      doAfter {
        service.shutdown()
      }

      "in json" in {
        // make some statsy things happen
        Stats.clearAll()
        Stats.time("kangaroo_time") { Stats.incr("kangaroos", 1) }

        val socket = new Socket("localhost", PORT)
        socket.getOutputStream().write("stats/json\n".getBytes)

        val stats = Json.parse(socket.getInputStream().readString(1024)).asInstanceOf[Map[String, Map[String, AnyRef]]]
        stats("jvm") must haveKey("uptime")
        stats("jvm") must haveKey("heap_used")
        stats("counters") must haveKey("kangaroos")
        stats("timings") must haveKey("kangaroo_time")

        val timing = stats("timings")("kangaroo_time").asInstanceOf[Map[String, Int]]
        timing("count") mustEqual 1
        timing("average") mustEqual timing("minimum")
        timing("average") mustEqual timing("maximum")
      }

      "in json, with reset" in {
        // make some statsy things happen
        Stats.clearAll()
        Stats.time("kangaroo_time") { Stats.incr("kangaroos", 1) }

        val socket = new Socket("localhost", PORT)
        socket.getOutputStream().write("stats/json reset\n".getBytes)
        val stats = Json.parse(socket.getInputStream().readString(1024)).asInstanceOf[Map[String, Map[String, AnyRef]]]
        val timing = stats("timings")("kangaroo_time").asInstanceOf[Map[String, Int]]
        timing("count") mustEqual 1

        val socket2 = new Socket("localhost", PORT)
        socket2.getOutputStream().write("stats/json reset\n".getBytes)
        val stats2 = Json.parse(socket2.getInputStream().readString(1024)).asInstanceOf[Map[String, Map[String, AnyRef]]]
        val timing2 = stats2("timings")("kangaroo_time").asInstanceOf[Map[String, Int]]
        timing2("count") mustEqual 0
      }

      "in text" in {
        // make some statsy things happen
        Stats.clearAll()
        Stats.time("kangaroo_time") { Stats.incr("kangaroos", 1) }

        val socket = new Socket("localhost", PORT)
        socket.getOutputStream().write("stats\n".getBytes)
        val response = socket.getInputStream().readString(1024).split("\n")
        response mustContain "  kangaroos: 1"
      }
    }
  }
}

