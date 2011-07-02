package com.twitter.ostrich.admin

trait Remote {

  def time(name: String, value: Int) 

}

class RemoteNoop extends Remote {

  override def time(name: String, value: Int) = {
    // does nothing
  }

}
