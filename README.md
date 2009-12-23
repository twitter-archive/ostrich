# Ostrich

Ostrich is a small library for collecting and reporting runtime statistics from a scala server. It
can collect counters, gauges, and timings, and it can report them via JMX, a simple web interface, a
plain-text socket, or a "W3C" log file. A server can also be asked to shutdown or reload its config
files using these interfaces.

The only dependencies are scala-json and configgy.

This library is released under the Apache Software License, version 2, which should be included with
the source in a file named `LICENSE`.


## Web/socket commands

Commands over the web interface take the form of a "get" request:

    GET /<command>[/<parameters...>]

which can be performed using 'curl' or 'wget':

    $ curl http://localhost:9990/shutdown

while over the plain-text socket, commands are simply typed as-is, followed by a linefeed:

    shutdown

Over the web interface, the result body is always a string of JSON. For simple commands like
`shutdown`, it may simply be the JSON encoding of the string "ok". For others like `stats`, it may
be a nested structure.

FIXME: describe ".txt" on http, and the socket listener.

The commands are:

- ping
- reload
- shutdown
- quiesce
- stats
- server_info

FIXME: describe those commands.


## API

FIXME.


## Credits

This started out as several smaller projects that began to overlap so much, we decided to merge
them. Major contributers include, in alphabetical order:

- Alex Payne
- John Kalucki
- Nick Kallen
- Pankaj Gupta
- Robey Pointer
- Steve Jenson

If you make a significant change, please add your name to the list!
