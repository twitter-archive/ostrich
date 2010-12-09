// Support for heapster profiling (google perftools compatible):
//
//   https://github.com/mariusaeriksen/heapster

package com.twitter.ostrich

import com.twitter.xrayspecs.Duration

class Heapster(klass: Class[_]) {
  private val startM = klass.getDeclaredMethod("start")
  private val stopM  = klass.getDeclaredMethod("stop")
  private val dumpProfileM =
    klass.getDeclaredMethod("dumpProfile", classOf[java.lang.Boolean])
  private val clearProfileM = klass.getDeclaredMethod("clearProfile")
  private val setSamplingPeriodM =
    klass.getDeclaredMethod("setSamplingPeriod", classOf[java.lang.Integer])

  def start() { startM.invoke(null) }
  def stop() { stopM.invoke(null) }
  def setSamplingPeriod(period: java.lang.Integer) { setSamplingPeriodM.invoke(null, period) }
  def clearProfile() { clearProfileM.invoke(null) }
  def dumpProfile(forceGC: java.lang.Boolean): Array[Byte] =
    dumpProfileM.invoke(null, forceGC).asInstanceOf[Array[Byte]]

  def profile(howlong: Duration, samplingPeriod: Int = 10<<19, forceGC: Boolean = true) = {
    clearProfile()
    setSamplingPeriod(samplingPeriod)

    start()
    Thread.sleep(howlong.inMilliseconds)
    stop()
    dumpProfile(forceGC)
  }
}

object Heapster {
  val instance: Option[Heapster] = {
    val loader = ClassLoader.getSystemClassLoader()
    try {
      Some(new Heapster(loader.loadClass("Heapster")))
    } catch {
      case _: ClassNotFoundException =>
        None
    }
  }
}
