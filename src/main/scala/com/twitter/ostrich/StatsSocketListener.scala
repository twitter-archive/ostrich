/*
 * Copyright 2009 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.ostrich

import net.lag.logging.Logger
import java.net.{ServerSocket, Socket}
import java.io.{BufferedReader, InputStream, InputStreamReader, IOException, PrintWriter, Writer}
import java.util.concurrent.Executors
import Conversions._


/**
 * Takes a Socket bound to a client and a function taking an input string and
 * returning a string to be written to the client socket. NB. It only reads
 * the first line and then closes the connection after writing the output.
 *
 * @param socket Socket bound to the client
 * @param fn Function for processing the first line coming from the client socket and returning
 *           a String to write back to the client socket.
 * @param timeout the socket timeout to set on the client socket.
 */
class StatsSocketWorker(socket: Socket, timeout: Int, fn: (String) => String) extends Runnable {
  private val log = Logger.get
  socket.setSoTimeout(timeout)

  /**
   * Sets the default socket timeout to 100ms.
   */
  def this(socket: Socket, fn: (String) => String) = this(socket, 100, fn)

  def run {
    log.ifDebug { "Client has connected to StatsSocketWorker from Address: %s:%s".format(socket.getInetAddress, socket.getPort) }
    var out: PrintWriter = null
    var in: BufferedReader = null
    try {
      out = new PrintWriter(socket.getOutputStream(), true)
      in = new BufferedReader(new InputStreamReader(socket.getInputStream))
      val line = in.readLine()
      log.ifDebug { "Reading in command from remote socket from Address: %s:%s".format(socket.getInetAddress, socket.getPort) }
      log.ifDebug { "Writing out response from StatsSocketWorker on Address: %s:%s".format(socket.getInetAddress, socket.getPort) }
      out.print(fn(line) + "\n")
      out.flush()
    } catch {
      case e: IOException => log.error(e, "Error writing to client")
    } finally {
      out.close()
      in.close()
      socket.close()
    }
  }
}


/**
 * StatsSocketListener listens on a port and runs a fn on the incoming command.
 * This assumes a simple single-line command text protocol.
 *
 * @param port the port to listen on
 * @param fn the function to run on each line of the incoming text
 * @param threads the number of threads devoted to serving requests
 */
class StatsSocketListener(val port: Int, threads: Int, fn: (String) => String) extends Runnable {
  /**
   * Constructor creating 3 threads to handle clients.
   */
  def this(port: Int, fn: (String) => String) = this(port, 3, fn)

  def this(port: Int) = this(port, cmd => {
    cmd match {
      case "stats" => Stats.stats(true).flatten
      case cmd => "Error! Unknown command: " + cmd
    }
  })

  val service = Executors.newFixedThreadPool(threads)
  val log = Logger.get
  val serverSocket: ServerSocket = new ServerSocket(port)

  serverSocket.setReuseAddress(true)
  log.info("StatsSocket now listening on port %s", port)

  /**
   * Blocking method that listens for connections and hands
   * them off to a worker thread for processing.
   */
  def run() {
    while (true) {
      try {
        log.debug("Waiting for client to connect")
        service.submit(new StatsSocketWorker(serverSocket.accept(), fn))
      } catch {
        case e: IOException => case e: IOException => log.error(e, "Error binding to socket")
      }
    }
  }

  def stop() = serverSocket.close()
}
