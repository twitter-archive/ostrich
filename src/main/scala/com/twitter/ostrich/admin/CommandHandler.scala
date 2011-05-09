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
package admin

import java.io._
import java.lang.management.ManagementFactory
import java.util.Date
import scala.collection.{JavaConversions, Map}
import scala.collection.immutable
import com.twitter.json.Json
import stats.{StatsListener, Stats}

class UnknownCommandError(command: String) extends IOException("Unknown command: " + command)

sealed abstract class Format()
object Format {
  case object PlainText extends Format
  case object Json extends Format
}

class CommandHandler(runtime: RuntimeEnvironment) {
  private def build(obj: Any): List[String] = {
    obj match {
      case m: Map[_, _] =>
        m.keys.map { _.toString }.toList.sorted.flatMap { k =>
          val value = m.asInstanceOf[Map[Any, Any]](k)
          build(value) match {
            case line :: Nil if (!line.contains(": ")) => List(k.toString + ": " + line)
            case list => (k.toString + ":") :: list.map { "  " + _ }
          }
        }
      case a: Array[_] =>
        a.flatMap { build(_) }.toList
      case s: Seq[_] =>
        s.flatMap { build(_) }.toList
      case d: Double =>
        if (d.longValue == d) List(d.longValue.toString) else List(d.toString)
      case x =>
        List(x.toString)
    }
  }

  def flatten(obj: Any): String = build(obj).mkString("\n") + "\n"

  def apply(command: String, parameters: Map[String, String], format: Format): String = {
    val rv = handleCommand(command, parameters)
    format match {
      case Format.PlainText =>
        flatten(rv)
      case Format.Json =>
        // force it into a map because some json clients expect the top-level object to be a map.
        Json.build(rv match {
          case x: Map[_, _] => x
          case _ => immutable.Map("response" -> rv)
        }).toString + "\n"
    }
  }

  def handleCommand(command: String, parameters: Map[String, String]): Any = {
    command match {
      case "ping" =>
        "pong"
      case "reload" =>
        BackgroundProcess.spawn("admin:reload") {
          ServiceTracker.reload()
        }
        "ok"
      case "shutdown" =>
        BackgroundProcess.spawn("admin:shutdown") {
          Thread.sleep(100)
          ServiceTracker.shutdown()
        }
        "ok"
      case "quiesce" =>
        BackgroundProcess.spawn("admin:quiesce") {
          Thread.sleep(100)
          ServiceTracker.quiesce()
        }
        "ok"
      case "stats" =>
        (parameters.get("namespace") match {
          case None => Stats.get()
          case Some(namespace) => StatsListener(namespace, Stats).get()
        }).toMap
      case "server_info" =>
        val mxRuntime = ManagementFactory.getRuntimeMXBean()
        immutable.Map("name" -> runtime.jarName,
                      "version" -> runtime.jarVersion,
                      "build" -> runtime.jarBuild,
                      "build_revision" -> runtime.jarBuildRevision,
                      "start_time" -> (new Date(mxRuntime.getStartTime())).toString,
                      "uptime" -> mxRuntime.getUptime())
      case "threads" =>
        getThreadStacks()
      case "gc" =>
        System.gc()
        "ok"
      case x =>
        throw new UnknownCommandError(x)
    }
  }

  private def getThreadStacks(): Map[String, Map[String, Map[String, Any]]] = {
    val stacks = JavaConversions.asScalaMap(Thread.getAllStackTraces()).map { case (thread, stack) =>
      (thread.getId().toString, immutable.Map("thread" -> thread.getName(),
                                              "daemon" -> thread.isDaemon(),
                                              "state" -> thread.getState(),
                                              "priority" -> thread.getPriority(),
                                              "stack" -> stack.toSeq.map(_.toString)))
    }.toSeq
    immutable.Map("threads" -> immutable.Map(stacks: _*))
  }
}
