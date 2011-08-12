This is a fork of Ostrich for Scala 2.9.  The official distribution of Ostrich can be found at: https://github.com/twitter/ostrich

This forks exists because we need a 2.9 build.  As soon as Twitter produce a 2.9 version, this repostory and the builds will be removed. For progress, see: https://github.com/twitter/ostrich/issues/29

This version of Ostrich depends on a Scala 2.9 version of Twitter utils:  https://github.com/d6y/util

## Test commented out

We happen to not use Ruby, so we have commented out one test which is causing problems for some trying to build Ostrich. See https://github.com/twitter/ostrich/issues/34

The change is we've made hasRuby=false in rc/test/scala/com/twitter/ostrich/stats/JsonStatsFetcherSpec.scala






