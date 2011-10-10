package com.twitter.ostrich.stats

/**
 * When a thread throws an Exception that was not caught, a DeathRattleExceptionHandler will
 * increment a counter signalling a thread has died and print out the name and stack trace of the
 * thread.
 *
 * This makes it easy to build alerts on unexpected Thread deaths and fine grained used quickens
 * debugging in production.
 *
 * You can also set a DeathRattleExceptionHandler as the default exception handler on all threads,
 * allowing you to get information on Threads you do not have direct control over.
 *
 * Usage is straightforward:
 *
 * {{{
 * val c = Stats.getCounter("my_runnable_thread_deaths")
 * val exHandler = new DeathRattleExceptionHandler(c)
 * val myThread = new Thread(myRunnable, "MyRunnable")
 * myThread.setUncaughtExceptionHandler(exHandler)
 * }}}
 *
 * Setting the global default exception handler should be done first, like so:
 * {{{
 * val c = Stats.getCounter("unhandled_thread_deaths")
 * val ohNoIDidntKnowAboutThis = new DeathRattleExceptionHandler(c)
 * Thread.setDefaultUncaughtExceptionHandler(ohNoIDidntKnowAboutThis)
 * }}}
 */
class DeathRattleExceptionHandler(deathRattle: Counter) extends Thread.UncaughtExceptionHandler {
  def uncaughtException(t: Thread, e: Throwable) {
    deathRattle.incr()
    System.err.println("Uncaught exception on thread " + t)
    e.printStackTrace()
  }
}