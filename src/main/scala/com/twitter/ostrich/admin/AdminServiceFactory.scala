package com.twitter.ostrich.admin

import com.twitter.conversions.time._
import com.twitter.logging.Logger
import com.twitter.ostrich.stats._
import com.twitter.util.Duration
import scala.util.matching.Regex

case class AdminServiceFactory(
    /**
     * HTTP port.
     */
    httpPort: Int,

    /**
     * Listen backlog for the HTTP port.
     */
    httpBacklog: Int = 20,

    /**
     * List of factories for stats nodes.
     * This is where you would define alternate stats collectors, or attach a json or w3c logger.
     */
    statsNodes: List[StatsFactory] = Nil,

    /**
     * The name of the stats collection to use. The default is "" which is the name for Stats.
     */
    statsCollectionName: Option[String] = None,

    /**
     * A list of regex patterns to filter out of reported stats when the "filtered" option is given.
     * This is useful if you know a bunch of stats are being reported that aren't interesting to
     * graph right now.
     */
    statsFilters: List[Regex] = Nil,

    /**
     * Extra handlers for the admin web interface.
     * Each key is a path prefix, and each value is the handler to invoke for that path. You can use
     * this to setup extra functionality for the admin web interface.
     */
    extraHandlers: Map[String, CustomHttpHandler] = Map(),

    /**
     * Default LatchedStatsListener intervals
     */
    defaultLatchIntervals: List[Duration] = 1.minute :: Nil)
  extends (RuntimeEnvironment => AdminHttpService) {

  def addStatsFactory(factory: StatsFactory): AdminServiceFactory =
    copy(statsNodes = factory :: statsNodes)

  def addStatsFilter(filter: Regex): AdminServiceFactory =
    copy(statsFilters = filter :: statsFilters)

  def addHandler(handler: (String, CustomHttpHandler)): AdminServiceFactory =
    copy(extraHandlers = extraHandlers + handler)

  def apply(runtime: RuntimeEnvironment): AdminHttpService = {
    configureStatsListeners(Stats)

    val statsCollection = statsCollectionName.map { Stats.make(_) }.getOrElse(Stats)
    val adminService = new AdminHttpService(httpPort, httpBacklog, statsCollection, runtime)

    for (factory <- statsNodes) {
      configureStatsListeners(factory(adminService))
    }

    adminService.start()

    // handlers can't be added until the admin server is started.
    extraHandlers.foreach { case (path, handler) =>
      adminService.addContext(path, handler)
    }

    adminService
  }

  def configureStatsListeners(collection: StatsCollection) = {
    defaultLatchIntervals foreach { StatsListener(_, collection, statsFilters) }
  }
}

case class StatsFactory(
    name: String = "",
    reporters: List[StatsReporterFactory] = Nil)
  extends (AdminHttpService => StatsCollection) {

  def apply(admin: AdminHttpService): StatsCollection = {
    val collection = Stats.make(name)
    for (factory <- reporters; reporter = factory(collection, admin)) {
      ServiceTracker.register(reporter)
      reporter.start()
    }
    collection
  }
}

abstract class StatsReporterFactory extends ((StatsCollection, AdminHttpService) => Service)

case class JsonStatsLoggerFactory(
    loggerName: String = "stats",
    period: Duration = 1.minute,
    serviceName: Option[String] = None,
    separator: String = "_")
  extends StatsReporterFactory {

  def apply(collection: StatsCollection, admin: AdminHttpService) =
    new JsonStatsLogger(Logger.get(loggerName), period, serviceName, collection, separator)
}

case class W3CStatsLoggerFactory(
    loggerName: String = "w3c",
    period: Duration = 1.minute)
  extends StatsReporterFactory {

  def apply(collection: StatsCollection, admin: AdminHttpService) =
    new W3CStatsLogger(Logger.get(loggerName), period, collection)
}

case class TimeSeriesCollectorFactory() extends StatsReporterFactory {
  def apply(collection: StatsCollection, admin: AdminHttpService) = {
    val service = new TimeSeriesCollector(collection)
    service.registerWith(admin)
    service
  }
}
