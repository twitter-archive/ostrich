package com.twitter.ostrich.admin

/**
 * This allows us to turn on and off Finagle's tracing.
 *
 * See: https://github.com/twitter/finagle
 */
class FinagleTracing(klass: Class[_]) {
  private val enableM = klass.getDeclaredMethod("enable")
  private val disableM  = klass.getDeclaredMethod("disable")

  def enable() { enableM.invoke(null) }
  def disable() { disableM.invoke(null) }
}

object FinagleTracing {
  val instance: Option[FinagleTracing] = {
    val loader = ClassLoader.getSystemClassLoader()
    try {
      Some(new FinagleTracing(loader.loadClass("com.twitter.finagle.tracing.Trace")))
    } catch {
      case _: ClassNotFoundException =>
        None
    }
  }
}
