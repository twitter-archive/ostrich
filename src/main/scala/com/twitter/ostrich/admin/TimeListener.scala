package com.twitter.ostrich.admin

trait TimeListener {

  def time(name: String, value: Int) 

}

class TimeListenerNoop extends TimeListener {

  override def time(name: String, value: Int) = {
    // does nothing
  }

}
