// Support for heapster profiling (google perftools compatible):
//
//   https://github.com/mariusaeriksen/heapster

package com.twitter.ostrich

import com.twitter.xrayspecs.Duration

class Heapster(klass: Class[_]) {
  private val startM = klass.getDeclaredMethod("start")
  private val stopM  = klass.getDeclaredMethod("stop")
  private val dumpProfileM = klass.getDeclaredMethod("dumpProfile")

  def start() { startM.invoke(null) }
  def stop() { stopM.invoke(null) }
  def dumpProfile(): Array[Byte] = dumpProfileM.invoke(null).asInstanceOf[Array[Byte]]

  def profile(howlong: Duration) = {
    start()
    Thread.sleep(howlong.inMilliseconds)
    stop()
    dumpProfile()
  }
}

object Heapster {
  val instance: Option[Heapster] = {
    val loader = ClassLoader.getSystemClassLoader()
    val heapsterClass = loader.loadClass("Heapster")
    if (heapsterClass ne null)
      Some(new Heapster(heapsterClass))
    else
      None
  }
}
