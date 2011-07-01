package com.twitter.ostrich.admin

/**
* Message to be transferred to remote server
*/
case class AddMetric(name: String, value: Int)

trait Remote {

  def time(name: String, value: Int) 

}

class RemoteNoop extends Remote {

  override def time(name: String, value: Int) = {
    // does nothing
  }

}
