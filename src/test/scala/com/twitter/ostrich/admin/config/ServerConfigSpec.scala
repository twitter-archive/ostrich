package com.twitter.ostrich.admin.config

import com.twitter.ostrich.admin.Service
import com.twitter.logging._
import com.twitter.logging.config.{FileHandlerConfig, LoggerConfig}
import com.twitter.ostrich.admin.RuntimeEnvironment
import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.scalatest.junit.JUnitRunner
import java.util.{logging => javalog}
import scala.util.Random

@RunWith(classOf[JUnitRunner])
class ServerConfigSpec extends FunSuite with BeforeAndAfter {
  val logLevel = Logger.levelNames(Option[String](System.getenv("log")).getOrElse("FATAL").toUpperCase)

  private val logger = Logger.get("")
  private var oldLevel: javalog.Level = _

  // we need to ensure the name is unique to avoid clobbering the same test run again on the same JVM
  val uniq = (new Random()).nextString(10)
  val fromFactoryName = "fromFactory" + uniq
  val fromConfigName = "fromConfig" + uniq

  before {
    oldLevel = logger.getLevel()
    logger.setLevel(logLevel)
    logger.addHandler(new ConsoleHandler(new Formatter(), None))
  }

  after {
    logger.clearHandlers()
    logger.setLevel(oldLevel)
  }

  class SampleService extends Service {
    def start() {}
    def shutdown() {}
  }

  class TestServerConfig extends ServerConfig[SampleService] {
    override def apply(runtime: RuntimeEnvironment): SampleService = new SampleService()
  }

  val sampleFactory = List(LoggerFactory(
    node = fromFactoryName,
    level = Some(Level.INFO),
    handlers = List(ScribeHandler(
      category = fromFactoryName
    ))
  ))

  val sampleConfig = List(new LoggerConfig {
    node = fromConfigName
    level = Some(Level.INFO)
    handlers = new FileHandlerConfig {
      filename = fromConfigName
    }
  })

  test("configure Logger with loggerConfig when LoggerFactories not specified") {
    val serverConfig = new TestServerConfig {
      override def loggerFactories = Nil
      loggers = sampleConfig
    }
    Logger.clearHandlers()
    serverConfig.configureLogging()
    assert(Logger.get(fromFactoryName).getHandlers().size == 0)
    assert(Logger.get(fromConfigName).getHandlers().size == 1)
  }

  test("configure Logger with loggerFactories when LoggerFactories specified") {
    val serverConfig = new TestServerConfig {
      override def loggerFactories = sampleFactory
      loggers = sampleConfig
    }
    Logger.clearHandlers()
    serverConfig.configureLogging()
    assert(Logger.get(fromFactoryName).getHandlers().size == 1)
    assert(Logger.get(fromConfigName).getHandlers().size == 0)
  }
}
