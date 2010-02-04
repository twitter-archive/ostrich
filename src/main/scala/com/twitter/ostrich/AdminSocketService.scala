/*
 * Copyright 2010 Twitter, Inc.
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

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import net.lag.configgy.{Configgy, ConfigMap, RuntimeEnvironment}
import net.lag.logging.Logger
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.group.{ChannelGroupFuture, ChannelGroupFutureListener, DefaultChannelGroup}
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.channel.{ChannelHandler, ChannelHandlerContext, ChannelPipeline, ChannelPipelineCoverage, ExceptionEvent, MessageEvent, SimpleChannelUpstreamHandler, ChannelStateEvent}
import org.jboss.netty.handler.codec.string.{StringDecoder, StringEncoder}


object AdminSocketService {
  val allChannels = new DefaultChannelGroup("AdminSocketService")
}


class AdminSocketService(config: ConfigMap, runtime: RuntimeEnvironment) extends Service {
  private val log = Logger.get
  val port = config.getInt("admin_text_port")

  val bootstrap = new ServerBootstrap(
    new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool())
  )

  val handler = new AdminSocketServiceHandler()
  val pipeline = bootstrap.getPipeline
  pipeline.addLast("encoder", new StringEncoder())
  pipeline.addLast("decoder", new StringDecoder())
  pipeline.addLast("handler", handler)

  def start() {
    port.map { port =>
      val channel = bootstrap.bind(new InetSocketAddress(port))
      AdminSocketService.allChannels.add(channel)
      ServiceTracker.register(this)
    }
  }

  override def quiesce() {
    shutdown()
  }

  override def shutdown() {
    val future: ChannelGroupFuture = AdminSocketService.allChannels.close()

    future.addListener(new ChannelGroupFutureListener() {
      def operationComplete(future: ChannelGroupFuture) {
        val completed = future.awaitUninterruptibly(500)
        log.debug("shutdown completed: " + completed)
        bootstrap.releaseExternalResources()
      }
    })
  }
}


@ChannelPipelineCoverage("all")
class AdminSocketServiceHandler extends SimpleChannelUpstreamHandler {
  private val log = Logger.get

  override def channelOpen(context: ChannelHandlerContext, event: ChannelStateEvent) {
    AdminSocketService.allChannels.add(context.getChannel())
  }

  override def messageReceived(context: ChannelHandlerContext, event: MessageEvent) {
    val line: String = event.getMessage().asInstanceOf[String]
    val request = line.split("\\s+").toList

    val (command, textFormat) = request.head.split("/").toList match {
      case Nil => throw new java.io.IOException("impossible")
      case x :: Nil => (x, "text")
      case x :: y :: xs => (x, y)
    }

    val format = textFormat match {
      case "json" => Format.Json
      case _ => Format.PlainText
    }

    val response: String = CommandHandler(command, request.tail, format)
    event.getChannel.write(response + "\n")
  }

  override def exceptionCaught(context: ChannelHandlerContext, event: ExceptionEvent) {
    log.warning("Unexpected exception from downstream: %s", event.getCause())
    event.getChannel.close()
  }
}
