# Ostrich

Ostrich is a small library for collecting and reporting runtime statistics and
debugging info from a scala server. It can collect counters, gauges, and
timings, and it can report them via log files or a simple web interface that
includes graphs. A server can also be asked to shutdown or reload its config
files using these interfaces. The idea is that it should be simple and
straightforward, allowing you to plug it in and get started quickly.

This library is released under the Apache Software License, version 2, which
should be included with the source in a file named `LICENSE`.


## Building

Use sbt (simple-build-tool) to build:

    $ sbt clean update package-dist

The finished jar will be in `dist/`.


## Counters, Gauges, Metrics, and Labels

There are four kinds of statistics that ostrich captures:

- counters

  A counter is a value that never decreases. Examples might be
  "`widgets_sold`" or "`births`". You just increment the counter each time a
  countable event happens, and graphing utilities usually graph the deltas
  over time. To increment a counter, use:

        stats.incr("births")

  or

        stats.incr("widgets_sold", 5)

- gauges

  A gauge is a value that has a discrete value at any given moment, like
  "`heap_used`" or "`current_temperature`". It's usually a measurement that
  you only need to take when someone asks. To define a gauge, stick this code
  somewhere in the server initialization:

        stats.addGauge("current_temperature") { myThermometer.temperature }

  A gauge method must always return a double.

- metrics

  A metric is tracked via distribution, and is usually used for timings, like
  so:

        stats.time("translation") {
          document.translate("de", "en")
        }

  But you can also add metrics directly:

        stats.addMetric("query_results", results.size)

  Metrics are collected by tracking the count, min, max, mean (average), and a
  simple bucket-based histogram of the distribution. This distribution can be
  used to determine median, 90th percentile, etc.

- labels

  A label is just a key/value pair of strings, usually used to report a
  subsystem's state, like "boiler=offline". They're set with:

        stats.setLabel("boiler", "online")

  They have no real statistical value, but can be used to raise flags in
  logging and monitoring.


## Quick Start

A good example server is created by the scala-build project here:
<http://github.com/twitter/scala-build>

Define a server config class:

    class MyServerConfig extends ServerConfig[MyServer] {
      var serverPort: Int = 9999

      def apply(runtime: RuntimeEnvironment) = {
        new MyServer(serverPort)
      }
    }

A `ServerConfig` class contains things you want to configure on your server,
as vars, and an `apply` method that turns a RuntimeEnvironment into your
server. `ServiceConfig` is actually a helper for `Config` that adds logging
configuration, sets up the optional admin HTTP server if it was configured,
and registers your service with the `ServiceTracker` so that it will be
shutdown when the admin port receives a shutdown command.

Next, make a simple config file for development:

    import com.twitter.admin.config._
    import com.twitter.conversions.time._
    import com.twitter.logging.config._
    import com.example.config._

    new MyServerConfig {
      serverPort = 9999
      admin.httpPort = 9900

      loggers = new LoggerConfig {
        level = Level.INFO
        handlers = new ConsoleHandlerConfig()
      }
    }

The config file will be evaluated at runtime by this code in your Main class:

    object Main {
      val log = Logger.get(getClass.getName)

      def main(args: Array[String]) {
        val runtime = RuntimeEnvironment(this, args)
        val server = runtime.loadRuntimeConfig[MyServer]()
        log.info("Starting my server!")
        try {
          server.start()
        } catch {
          case e: Exception =>
            e.printStackTrace()
            log.error(e, "Unexpected exception: %s", e.getMessage)
            System.exit(0)
        }
      }
    }

Your `MyServer` class should implement the `Service` interface so it can be
started and shutdown. The runtime environment will find your config file and
evaluate it, returning the `MyServer` object to you so you can start it. And
you're set!


## Stats API

The base trait of the stats API is `StatsProvider`, which defines methods for
setting and getting each type of collected stat. The concrete implementation
is `StatsCollection`, which stores them all in java concurrent hash maps.

To log or report stats, attach a `StatsReporter` to a `StatsCollection`. A
`StatsReporter` keeps its own state, and resets that state each time it
reports. You can attach multiple `StatsReporter`s to track independent state
without affecting the `StatsCollection`.


## ServiceTracker

The global "shutdown" and "quiesce" commands work by talking to a global
`ServiceTracker` object. This is just a set of running `Service` objects.

Each `Service` knows how to start and shutdown, so registering a service with
the global `ServiceTracker` will cause it to be shutdown when the server as a
whole is shutdown:

    ServiceTracker.register(this)

Some helper classes like `BackgroundProcess` and `PeriodicBackgroundProcess`
implement `Service`, so they can be used to build simple background tasks
that will be automatically shutdown when the server exits.


## Web/socket commands

Commands over the admin interface take the form of an HTTP "get" request:

    GET /<command>[/<parameters...>][.<type>]

which can be performed using 'curl' or 'wget':

    $ curl http://localhost:9990/shutdown

The result body may be json or plain-text, depending on <type>. The default is
json, but you can ask for text like so:

    $ curl http://localhost:9990/stats.txt

For simple commands like `shutdown`, the response body may simply be the JSON encoding of the string
"ok". For others like `stats`, it may be a nested structure.

The commands are:

- ping

  verify that the admin interface is working; server should say "pong" back

- reload

  reload the server config file with `Configgy.reload()`

- shutdown

  immediately shutdown the server

- quiesce

  close any listening sockets, stop accepting new connections, and shutdown the server as soon as
  the last client connection is done

- stats

  dump server statistics as 4 groups: counters, gauges, metrics, and labels

- server_info

  dump server info (server name, version, build, and git revision)

- threads

  dump stack traces and stats about each currently running thread

- gc

  force a garbage collection cycle


## Web graphs

The web interface also includes a small graph server that can be used to look at the last hour of
data on collected stats. (See "Stats API" below for how to track stats.)

The url

    http://localhost:PPPP/graph/

(where PPPP is your `admin_http_port`) will give a list of currently-collected stats, and links to
the current hourly graph for each stat. The graphs are generated in javascript using flot.


## Admin API

The easiest way to start the admin service is to construct an `AdminServiceConfig` with desired
configuration, and call `apply` on it.

    val admin = new AdminServiceConfig {
      httpPort = 8888
      statsNodes = new StatsConfig {
        reporters = new TimeSeriesCollectorConfig
      }
    }
    admin()

If `httpPort` isn't set, the admin server won't start.

A helper trait called `ServerConfig` contains an `AdminServiceConfig` and `LoggerConfig` to reduce
boilerplate in the common case of configuring a server.

To build the admin service manually, you can do what the config classes do:

    val runtime = RuntimeEnvironment(this, Nil)
    val admin = new AdminHttpService(/* port */ 8888, /* http backlog */ 20, runtime)
    val collector = new TimeSeriesCollector(Stats)
    collector.registerWith(admin)
    ServiceTracker.register(collector)
    collector.start()


## Profiling

If you're using [heapster](https://github.com/mariusaeriksen/heapster), you can generate a profile
suitable for reading with [google perftools](http://code.google.com/p/google-perftools/)

Example use:

    curl -s 'localhost:9990/pprof/heap?pause=10' >| /tmp/prof

This will result in a file that you can be read with
[pprof](http://goog-perftools.sourceforge.net/doc/cpu_profiler.html)


## Credits

This started out as several smaller projects that began to overlap so much, we decided to merge
them. Major contributers include, in alphabetical order:

- Alex Payne
- John Corwin
- John Kalucki
- Marius Eriksen
- Nick Kallen
- Pankaj Gupta
- Robey Pointer
- Steve Jenson

If you make a significant change, please add your name to the list!
