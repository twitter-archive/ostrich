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

import com.twitter.conversions.time._
import com.twitter.ostrich.util.Json
import com.twitter.jvm.ContentionSnapshot
import com.twitter.logging.Logger
import com.twitter.util.Duration
import java.io._
import java.lang.management.ManagementFactory
import java.util.Date
import java.util.regex.Pattern
import scala.collection.JavaConverters._
import scala.collection.Map
import scala.collection.immutable
import stats.{StatsListener, StatsCollection}

class UnknownCommandError(command: String) extends IOException("Unknown command: " + command)
class InvalidCommandOptionError(
    command: String,
    optionName: String,
    optionValue: String)
  extends IllegalArgumentException(
    "Invalid option for " + command + " command: " + optionName + ':' + optionValue)

sealed abstract class Format()
object Format {
  case object PlainText extends Format
  case object Json extends Format
}

class CommandHandler(
  runtime: RuntimeEnvironment,
  statsCollection: StatsCollection,
  statsListenerMinPeriod: Duration
) {
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
      case x if x == null =>
        List("null")
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
        }) + "\n"
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
      case "logging" =>
        changeLoggingLevel(parameters)
      case "stats" =>
        val filtered = parameters.get("filtered").getOrElse("0") == "1"
        parameters.get("period").map { period =>
          // listener for a given period
          val periodDuration = period.toInt.seconds
          if (periodDuration < statsListenerMinPeriod) {
            throw new InvalidCommandOptionError(
              command, "statsListenerMinPeriod", statsListenerMinPeriod.toString)
          }

          StatsListener(periodDuration, statsCollection).get(filtered)
        }.orElse {
          // (archaic, deprecated) named listener
          parameters.get("namespace").map { namespace =>
            StatsListener(namespace, statsCollection).get(filtered)
          }
        }.getOrElse {
          // raw, un-delta'd data
          statsCollection.get()
        }.toMap
      case "server_info" =>
        val mxRuntime = ManagementFactory.getRuntimeMXBean()
        immutable.Map(
          "name" -> runtime.jarName,
          "version" -> runtime.jarVersion,
          "build" -> runtime.jarBuild,
          "build_revision" -> runtime.jarBuildRevision,
          "build_branch_name" -> runtime.jarBuildBranchName,
          "build_last_few_commits" -> runtime.jarBuildLastFewCommits.split("\n"),
          "start_time" -> (new Date(mxRuntime.getStartTime())).toString,
          "uptime" -> mxRuntime.getUptime()
        )
      case "threads" =>
        getThreadStacks()
      case "gc" =>
        System.gc()
        "ok"
      case "contention" =>
        getContentionSnapshot()
      case x =>
        throw new UnknownCommandError(x)
    }
  }

  private def changeLoggingLevel(parameters: Map[String, String]): String = {
    def helpMessage: String = {
      val loggingLevels = Logger.levelNames.keys.mkString("\n")
      val loggerNames = Logger.iterator.map { logger => logger.name }.toSeq.sorted.mkString("\n")
      "Supported Levels:\n%s\nValid Loggers:%s".format(loggingLevels, loggerNames)
    }

    val nameRegexOpt = parameters.get("name")
    val levelNameOpt = parameters.get("level")

    if (nameRegexOpt.isEmpty || levelNameOpt.isEmpty) {
      "Specify a logger name and level\n\n%s".format(helpMessage)
    } else {
      val nameRegex = nameRegexOpt.get
      val levelName = levelNameOpt.get
      val pattern: Pattern = Pattern.compile(nameRegex)
      val updatedLoggers: Option[Seq[String]] = Logger.levelNames.get(levelName.toUpperCase).map { level =>
        val loggers = Logger.iterator.filter {
          logger => pattern.matcher(logger.name).matches()
        }.toSeq
        loggers foreach { _.setLevel(level) }
        loggers map { _.name }
      }

      updatedLoggers match {
        case None => "Logging level change failed for %s to %s\n\n%s".format(nameRegex, levelName, helpMessage)
        case Some(Nil) => "Logging level change failed for %s to %s\n\n%s".format(nameRegex, levelName, helpMessage)
        case Some(loggers) =>
          val buf = new StringBuilder()
          buf.append("Successfully changed the level of the following logger(s) to ").append(levelName).append("\n")
          loggers.foreach { buf.append("\t").append(_).append("\n") }
          buf.toString
      }
    }
  }


  private def getThreadStacks(): Map[String, Map[String, Map[String, Any]]] = {
    val stacks = Thread.getAllStackTraces().asScala.map { case (thread, stack) =>
      (thread.getId().toString, immutable.Map("thread" -> thread.getName(),
                                              "daemon" -> thread.isDaemon(),
                                              "state" -> thread.getState(),
                                              "priority" -> thread.getPriority(),
                                              "stack" -> stack.toSeq.map(_.toString)))
    }.toSeq
    immutable.Map("threads" -> immutable.Map(stacks: _*))
  }

  private val contentionSnapshotter = new ContentionSnapshot
  private def getContentionSnapshot(): Map[String, Seq[String]] = {
    val snapshot = contentionSnapshotter.snap()
    Map(
      ("blocked_threads" -> snapshot.blockedThreads),
      ("lock_owners" -> snapshot.lockOwners))
  }
}
