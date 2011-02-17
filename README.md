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


### Logging

To access logging, you can usually just use:

    import com.twitter.logging.Logger
    private val log = Logger.get(getClass)

This creates a `Logger` object that uses the current class or object's
package name as the logging node, so class "com.example.foo.Lamp" will log to
node "com.example.foo" (generally showing "foo" as the name in the logfile).
You can also get a logger explicitly by name:

    private val log = Logger.get("com.example.foo")

Logger objects wrap everything useful from "java.util.logging.Logger", as well
as adding some convenience methods:

    // log a string with sprintf conversion:
    log.info("Starting compaction on level %d...", level)

    try {
      ...
    } catch {
      // log an exception backtrace with the message:
      case e: IOException =>
        log.error(e, "I/O exception: %s", e.getMessage)
    }

Each of the log levels (from "fatal" to "trace") has these two convenience
methods. You may also use `log` directly:

    log(Logger.DEBUG, "Logging %s at debug level.", name)

An advantage to using sprintf ("%s", etc) conversion, as opposed to:

    log(Logger.DEBUG, "Logging " + name + " at debug level.")

is that java & scala perform string concatenation at runtime, even if nothing
will be logged because the logfile isn't writing debug messages right now.
With sprintf parameters, the arguments are just bundled up and passed directly
to the logging level before formatting. If no log message would be written to
any file or device, then no formatting is done and the arguments are thrown
away. That makes it very inexpensive to include excessive debug logging which
can be turned off without recompiling and re-deploying.

If you prefer, there are also variants that take lazy-evaluated parameters,
and only evaluate them if logging is active at that level:

    log.ifDebug("Login from " + name + " at " + date + ".")

The logging classes are done as an extension to the `java.util.logging` API,
and so you can use the java interface directly, if you want to. Each of the
java classes (Logger, Handler, Formatter) is just wrapped by a scala class
with a cleaner interface.


## Quick Start

A good example server is created by the scala-build project here:
<http://github.com/twitter/scala-build>

Define a server config class:

    class MyServerConfig extends Config[RuntimeEnvironment => MyServer] {
      var loggers: List[LoggerConfig] = Nil
      var admin = new AdminServiceConfig()
    
      var serverPort: Int = 9999
    
      def apply() = { (runtime: RuntimeEnvironment) =>
        Logger.configure(loggers)
        admin()(runtime)
        val server = new MyServer(serverPort)
        ServiceTracker.register(server)
        server
      }
    }

A config class contains things you want to configure on your server, as vars,
and an `apply` method that returns a method turning a RuntimeEnvironment into
your server. The first two lines of the `apply` method configure logging and
set up the optional admin HTTP server if it was configured. Your server object
is created, then registered with the `ServiceTracker` so that it will be
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



--- everything below here needs work.
## Web/socket commands
## Web graphs
## Admin API
## Config keys
## Profiling
## Credits
## Basic Use ### Logging
## Advanced Features ### Logging
## Advanced Features ### Logging with scribe



## Web/socket commands

Commands over the web interface take the form of a "get" request:

    GET /<command>[/<parameters...>][.<type>]

which can be performed using 'curl' or 'wget':

    $ curl http://localhost:9990/shutdown

The result body may be json or plain-text, depending on <type>. The default is
json, but you can ask for text like so:

    $ curl http://localhost:9990/stats/reset.txt

or:

    stats/json reset

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

- stats [reset]

  dump server statistics as 4 groups: JVM-specific, gauges, counters, and timings; if "reset" is
  added, the counters and timings are atomically cleared as they are dumped

- server_info

  dump server info (server name, version, build, and git revision)

- threads

  dump stack traces and stats about each currently running thread


## Web graphs

The web interface also includes a small graph server that can be used to look at the last hour of
data on collected stats. (See "Stats API" below for how to track stats.)

The url

    http://localhost:PPPP/graph/

(where PPPP is your `admin_http_port`) will give a list of currently-collected stats, and links to
the current hourly graph for each stat. The graphs are generated in javascript using flot.


## Admin API

To startup the admin interfaces, call:

    ServiceTracker.startAdmin(config, runtimeEnvironment)

`RuntimeEnvironment` comes from configgy, and is used to display the server info.

`Config` is usually your root server config (but doesn't have to be) and is used to determine which
admin interfaces to start up. If `admin_text_port` exists, the socket interface will start up there.
If `admin_http_port` exists, the web interface will start up. If neither is set, no admin services
will be started.

In order to shutdown your server from the admin port, you must implement `Service` and register it:

    ServiceTracker.register(this)

`Service` contains only the methods `shutdown` and `quiesce`, both of which are always called from
dedicated temporary threads (so it's okay to do slow things, but be careful of thread safety). You
can implement `quiesce` as a call to `shutdown` if the distinction makes no sense for your server.

An example:

    import com.twitter.ostrich.{Server, ServerInterface}
    import net.lag.configgy.{Configgy, RuntimeEnvironment}

    object Main extends Service {
      val runtime = new RuntimeEnvironment(getClass)
      runtime.load(args)
      val config = Configgy.config
      ServiceTracker.register(this)
      ServiceTracker.startAdmin(config, runtime)


## Config keys

- `admin_http_port`

  port for the web server interface (default: no web interface)

- `admin_text_port`

  port for the interactive text interface (default: no text interface)

- `admin_jmx_package`

  package to use for reporting stats & config through JMX (default: no JMX)

- `admin_timeseries`

  true/false, whether to expose the hourly graphs through the web interface (default: true)







  
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
- John Kalucki
- Nick Kallen
- Pankaj Gupta
- Robey Pointer
- Steve Jenson
- John Corwin

If you make a significant change, please add your name to the list!


















## Quick Start

This is all you need to know to use the library:

    import net.lag.configgy.Configgy
    import net.lag.logging.Logger

    // load our config file and configure logfiles:
    Configgy.configure("/etc/pingd.conf")
    
    // read hostname and port:
    val config = Configgy.config
    val hostname = config.getString("hostname", "localhost")
    val port = config.getInt("port", 3000)
    
    // log an error:
    val log = Logger.get
    log.error("Unable to listen on %s:%d!", hostname, port)
    
    // or an exception:
    try {
      ...
    } catch {
      case e: IOException => log.error(e, "IOException while doodling")
    }

The following config file will setup a logfile at debug level, that rolls
every night, and also sets a few simple config values:

    log {
      filename = "/var/log/pingd.log"
      roll = "daily"
      level = "debug"
    }
    
    hostname = "pingd.example.com"
    port = 3000

The rest of this README just describes the config file format, the logging
options, and how to use the library in more detail.


## Basic Use


### Logging

Logging is configured in a special `log` block. The main logging options
are described below.

- `filename` -
  the file to write log entries into (optional)
- `level` -
  the lowest severity log entry that should be written to the
  logfile (defaults to `INFO`) (described below)
- `console` -
  `true` (`on`) if logs should be written to the stderr
  console
- `syslog_host` -
  hostname (or `hostname:port`) to send syslog formatted
  log data to (optional)
- `syslog_server_name` -
  server name to attach to log messages when
  sending to a syslog (optional)
- `roll` -
  when the logfile should be rolled (described below)

Logging severities are:

    Severity   Description
    =========  ================
    FATAL      the server is about to exit
    CRITICAL   something happened that is so bad that someone should probably
                 be paged
    ERROR      an error occurred that may be limited in scope, but was
                 user-visible
    WARNING    a coder may want to be notified, but the error was probably not
                 user-visible
    INFO       normal informational logging
    DEBUG      coder-level debugging information
    TRACE      intensive debugging information

Logfile rolling policies are:

    Name     Description
    =======  =================
    never    always use the same logfile
    hourly   roll to a new logfile at the top of every hour
    daily    roll to a new logfile at midnight every night
    sunday   roll to a new logfile at midnight between saturday and sunday,
               once a week

You can omit a rolling policy, or use policy "never", to avoid rolling the
logfiles. For weekly logfile rolling, you may use any day of the week
("monday", "tuesday", etc), not just "sunday".

When a logfile is rolled, the current logfile is renamed to have the date (and
hour, if rolling hourly) attached, and a new one is started. So, for example,
`test.log` may become `test-20080425.log`, and `test.log` will be
reopened as a new file.

So, for example:

    log {
      filename = "test.log"
      level = "warning"
      roll = "tuesday"
    }

creates a logfile `test.log` that captures log entries only at warning,
error, critical, or fatal levels. It's rolled once a week, at midnight on
Tuesday morning.

None of `filename`, `console`, or `syslog_host` are mutually exclusive,
so you can define any or all of those targets, to have log messages sent to
any possible combination of places.

## Advanced Features

There are a few features you may not use right away, but you'll usually
start wanting after the code matures a bit.



### Logging

There are a handful of options to tune logging more directly:

- `utc` -
  `on` to log in UTC (previously known as GMT) time instead of local
  time (default: off)
- `truncate` -
  number of characters to allow in a single log line before eliding with
  "..." (default: 0 = never truncate)
- `truncate_stack_traces` -
  number of lines of a stack trace to show before eliding (default: 30)
- `syslog_use_iso_date_format` -
  set `off` to use old-style BSD date format in syslog messages
  (default: on)
- `use_full_package_names` -
  set `on` to use full package names in log lines ("net.lag.configgy")
  instead of the toplevel node ("configgy") (default: off)
- `append` -
  set `off` to create a new logfile each time the app starts (default:
  on, meaning to append to any existing logfile)
- `format` -
  sets the overall output format. `bare` means no formatting. `exception_json`
  logs throwables in json format.
  (default: use a generic formatter, which honors `prefix_format`)
- `prefix_format` -
  when using the generic formatter, customize the format of log line prefixes (see below)
- `throttle_period_msec`, `throttle_rate` -
  throttle log messages going to this output. `throttle_rate` defines the number of lines
  per `throttle_period_msec` to allow before squelching. The messages are uniquely identified
  by their pre-printf formatting pattern.
- `handle_sighup` -
  if set to true, attaches a handler to the HUP signal which causes the logger to
  reopen its logfile. This allows configgy to work well with external log rotation
  tools.

The logging options are usually set on the root node of java's "logging tree",
at "". You can set options or logging handlers at other nodes by putting them
in config blocks inside `<log>`, and specifying a node name. For example:

    log {
      filename = "test.log"
      level = "warning"
      utc = true
      
      squelch_noisy {
        node = "com.example.libnoise"
        level = "critical"
      }
    }

(You don't have to name the block "squelch_noisy"; they can have any name.)

The "com.example.libnoise" node will log at "critical" level (presumably to
silence a noisy library), while everything else will log at "warning" level.
You can put any of the logging options inside these blocks, including those
for logging to files or syslog nodes. Also, you can have multiple blocks per
node, so you can attach multiple output files and handlers to a single log node.
In this way you can create multiple logfiles, or have log lines go to multiple
places, such as syslog or scribe.

The extra options you can use in these inner blocks are:

- `node` -
  define the log node name (as a string). (default: use the root "" logger)
- `use_parents` -
  whether to fall back to parent log-node configuration (java's 
  `setUseParentHandlers`) (default: on)

A custom log line prefix format can be set with `prefix_format`. The date
format should be between `<` angle brackets `>` in the form used by java's
`SimpleDateFormat` and the rest of the string is passed through java's
`String.format()`, with the log level as the first parameter and the logger
name as the second. For example, a format string of:

    %.3s [<yyyyMMdd-HH:mm:ss.SSS>] %s:

will generate a log line prefix of:

    ERR [20080315-18:39:05.033] julius:


### Logging with scribe

A config node can be directed to a scribe server instead of, or in addition
to, a file or console. To do this, configure a scribe server on the node:

    log {
      scribe_server = "scribe1.corp"
      scribe_category = "echod"
      level = "info"
    }

Configgy will try to keep a persistent connection open to the designated
scribe server, and bundle up log messages to limit the number of thrift API
requests. If the scribe server disconnects, configgy will automatically try to
reconnect. If the server is persistently down, configgy will only retry
periodically, and buffer as much as it reasonably can.

The following options can be set for scribe logging:

- `scribe_server` -
  scribe server hostname, with optional port number (`"localhost:9463"`)
- `scribe_category` -
  server name to use (default is "scala")
- `scribe_buffer_msec` -
  how long to buffer log messages before flushing them to the scribe server
  (default: 100 msec)
- `scribe_max_packet_size` -
  maximum number of log lines to send to the scribe server in a single
  request (default: 1000)
- `scribe_max_buffer` -
  maximum number of log lines to buffer for scribe before dropping them
  (default: 10000)
- `scribe_backoff_msec` -
  if the scribe server goes offline, don't try to reconnect more often than
  this (default: 15000 msec, or 15 sec)



