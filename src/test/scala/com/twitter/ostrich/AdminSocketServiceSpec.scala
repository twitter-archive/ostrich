package com.twitter.ostrich

import java.io.{DataInputStream, InputStream}
import java.net.{Socket, SocketException}
import com.twitter.json.Json
import com.twitter.xrayspecs.Eventually
import net.lag.configgy.{Config, RuntimeEnvironment}
import org.mockito.Matchers._
import org.specs.Specification
import org.specs.mock.Mockito


object AdminSocketServiceSpec extends Specification with Eventually with Mockito {
  val PORT = 9995
  val config = Config.fromMap(Map("admin_http_port" -> PORT.toString))

  "AdminSocketService" should {
    "start up" in {
      val service = new AdminSocketService(config, new RuntimeEnvironment(getClass))
      service.start()
      // service.shutdown()
      1 mustEqual 1
    }
  }
}

