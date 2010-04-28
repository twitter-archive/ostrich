# Ostrich

Ostrich is a small library for collecting and reporting runtime statistics from a scala server. It
can collect counters, gauges, and timings, and it can report them via JMX, a simple web interface, a
plain-text socket, or a "W3C" log file. A server can also be asked to shutdown or reload its config
files using these interfaces.

Dependencies: scala-json, Configgy, Netty. These dependencies are managed by the build system.

This library is released under the Apache Software License, version 2, which should be included with
the source in a file named `LICENSE`.


## Web/socket commands

Commands over the web interface take the form of a "get" request:

    GET /<command>[/<parameters...>][.<type>]

which can be performed using 'curl' or 'wget':

    $ curl http://localhost:9990/shutdown

while over the plain-text socket, commands are simply typed as-is, followed by a linefeed:

    <command>[/<type>] <parameters...>

The result body may be json or plain-text, depending on <type>. Over the web interface, the default
is json, but over the socket interface, the default is plain-text. You can override these defaults
like so:

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


## Admin API

To startup the admin interfaces, call:

    Server.startAdmin(serverInterface, config, runtimeEnvironment)

`RuntimeEnvironment` comes from configgy, and is used to display the server info.

`Config` is usually your root server config (but doesn't have to be) and is used to determine which
admin interfaces to start up. If `admin_text_port` exists (usually 9989), the socket interface will
start up there. If `admin_http_port` exists (usually 9990), the web interface will start up. If
neither is set, no admin services will be started.

`ServerInterface` is your implementation of `ServerInterface` for your server. It contains only the
methods `shutdown` and `quiesce`, both of which are always called from dedicated temporary threads
(so it's okay to do slow things, but be careful of thread safety). You can implement `quiesce` as a
call to `shutdown` if the distinction makes no sense for your server.

An example:

    import com.twitter.ostrich.{Server, ServerInterface}
    import net.lag.configgy.{Configgy, RuntimeEnvironment}

    object Main extends ServerInterface {
      val runtime = new RuntimeEnvironment(getClass)
      runtime.load(args)
      val config = Configgy.config
      Server.startAdmin(this, config, runtime)


## Stats API

There are three kinds of statistics that ostrich captures, in addition to the stardard JVM
reporting:

- counters

  A counter is a value that never decreases. Examples might be "`widgets_sold`" or "`births`". You
  just click the counter each time a countable event happens, and graphing utilities usually graph
  the deltas over time. To increment a counter, use:

        Stats.incr("births")

  or

        Stats.incr("widgets_sold", 5)

- gauges

  A gauge is a value that has a discrete value at any given moment, like "`heap_used`" or
  "`current_temperature`". It's usually a measurement that you only need to take when someone asks.
  To define a gauge, stick this code somewhere in the server initialization:

        Stats.makeGauge("current_temperature") { myThermometer.getTemperatureInCelcius() }

  A gauge method must always return a double.

- timings

  A timing is a stopwatch timer around code, like so:

        Stats.time("translation") {
          document.translate("de", "en")
        }

  Timings are collected in aggregate, and the aggregation is reported through the "`stats`" command.
  The aggregation includes the count (number of timings performed), sum, maximum, minimum, average,
  standard deviation, and sum of squares (useful for aggregating the standard deviation).

There are several other useful methods for creating derivative gauges or capturing timings -- check
out the code.


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
