# Ostrich

Ostrich is a library for scala servers that makes it easy to:

- load & reload per-environment configuration
- collect runtime statistics (counters, gauges, metrics, and labels)
- report those statistics through a simple web interface (optionally with
  graphs) or into log files
- interact with the server over HTTP to check build versions or shut it down

The idea is that it should be simple and straightforward, allowing you to
plug it in and get started quickly.

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

        Stats.incr("births")

  or

        Stats.incr("widgets_sold", 5)

- gauges

  A gauge is a value that has a discrete value at any given moment, like
  "`heap_used`" or "`current_temperature`". It's usually a measurement that
  you only need to take when someone asks. To define a gauge, stick this code
  somewhere in the server initialization:

        Stats.addGauge("current_temperature") { myThermometer.temperature }

  A gauge method must always return a double.

- metrics

  A metric is tracked via distribution, and is usually used for timings, like
  so:

        Stats.time("translation") {
          document.translate("de", "en")
        }

  But you can also add metrics directly:

        Stats.addMetric("query_results", results.size)

  Metrics are collected by tracking the count, min, max, mean (average), and a
  simple bucket-based histogram of the distribution. This distribution can be
  used to determine median, 90th percentile, etc.

- labels

  A label is just a key/value pair of strings, usually used to report a
  subsystem's state, like "boiler=offline". They're set with:

        Stats.setLabel("boiler", "online")

  They have no real statistical value, but can be used to raise flags in
  logging and monitoring.


## RuntimeEnvironment

If you build with standard-project
<http://github.com/twitter/standard-project>, `RuntimeEnvironment` can pull
build and environment info out of the `build.properties` file that's tucked
into your jar. Typical use is to pass your server object (or any object from
your jar) and any command-line arguments you haven't already parsed:

    val runtime = RuntimeEnvironment(this, args)

The command-line argument parsing is optional, and supports only:

- `--version` to print out the jar's build info (name, version, build)

- `-f <filename>` to specify a config file manually

- `--validate` to validate that your config file can be compiled

Your server object is used as the home jar of the `build.properties` file.
Then the classpath is scanned to find that jar's home and the config files
that are located nearby.


## Quick Start

A good example server is created by the scala-bootstrapper project here:
<http://github.com/twitter/scala-bootstrapper>

Define a server config class:

    class MyServerConfig extends ServerConfig[MyServer] {
      var serverPort: Int = 9999

      def apply(runtime: RuntimeEnvironment) = {
        new MyServer(serverPort)
      }
    }

A `ServerConfig` class contains things you want to configure on your server,
as vars, and an `apply` method that turns a RuntimeEnvironment into your
server. `ServerConfig` is actually a helper for `Config` that adds logging
configuration, sets up the optional admin HTTP server if it was configured,
and registers your service with the `ServiceTracker` so that it will be
shutdown when the admin port receives a shutdown command.

Next, make a simple config file for development:

    import com.twitter.conversions.time._
    import com.twitter.logging.config._
    import com.twitter.ostrich.admin.config._
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

The simplest (and most common) pattern is to use the global singleton named
`Stats`, like so:

    import com.twitter.ostrich.stats.Stats

    Stats.incr("cache_misses")
    Stats.time("memcache_timing") {
      memcache.set(key, value)
    }

Stat names can be any string, though conventionally they contain only letters,
digits, underline (_), and dash (-), to make it easier for reporting.

You can immediately see any reported stats on the admin web server, if you've
activated it, through the "stats" command:

    curl localhost:PPPP/stats.txt

(where `PPPP` is your configured admin port)


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


## Admin web service

The easiest way to start the admin service is to construct an
`AdminServiceConfig` with desired configuration, and call `apply` on it.

To reduce boilerplate in the common case of configuring a server with an
admin port and logging, a helper trait called `ServerConfig` is defined with
both:

    var loggers: List[LoggerConfig] = Nil
    var admin = new AdminServiceConfig()

The `apply` method on `ServerConfig` will create and start the admin service
if a port is defined, and setup any configured logging.

You can also build an admin service directly from its config:

    val adminConfig = new AdminServiceConfig {
      httpPort = 8888
      statsNodes = new StatsConfig {
        reporters = new TimeSeriesCollectorConfig
      }
    }
    val runtime = RuntimeEnvironment(this, Nil)
    val admin = adminConfig()(runtime)

If `httpPort` isn't set, the admin service won't start, and `admin` will be
`None`. Otherwise it will be an `Option[AdminHttpService]`.

`statsNodes` can attach a list of reporters to named stats collections. In the
above example, a time-series collector is added to the global `Stats` object.
This is used to provide the web graphs described below under "Web graphs".


## Web/socket commands

Commands over the admin interface take the form of an HTTP "get" request:

    GET /<command>[/<parameters...>][.<type>]

which can be performed using 'curl' or 'wget':

    $ curl http://localhost:PPPP/shutdown

The result body may be json or plain-text, depending on <type>. The default is
json, but you can ask for text like so:

    $ curl http://localhost:PPPP/stats.txt

For simple commands like `shutdown`, the response body may simply be the JSON
encoding of the string "ok". For others like `stats`, it may be a nested
structure.

The commands are:

- ping

  Verify that the admin interface is working; server should say "pong" back.

- reload

  Reload the server config file for any services that support it (most do not).

- shutdown

  Immediately shutdown the server.

- quiesce

  Close any listening sockets, stop accepting new connections, and shutdown the server as soon as
  the last client connection is done.

- stats

  Dump server statistics as 4 groups: counters, gauges, metrics, and labels.

  Normally you want to add a `namespace` argument, which will create a new listener for the given
  name. For example, `/stats.json?namespace=ganglia` lets ganglia fetch stats using its own
  listener. (See `src/scripts/json_stats_fetcher.rb` for an example.) If you omit a namespace, the
  main stats object will be fetched, and metrics will be globally reset each time.

- server_info

  Dump server info (server name, version, build, and git revision).

- threads

  Dump stack traces and stats about each currently running thread.

- gc

  Force a garbage collection cycle.


## Web graphs

If `TimeSeriesCollector` is attached to a stats collection, the web interface
will include a small graph server that can be used to look at the last hour of
data on collected stats.

The url

    http://localhost:PPPP/graph/

(where PPPP is your admin `httpPort`) will give a list of currently-collected
stats, and links to the current hourly graph for each stat. The graphs are
generated in javascript using flot.


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
