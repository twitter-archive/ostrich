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
package admin

import java.io.File
import java.util.Properties
import scala.collection.mutable
import com.twitter.conversions.string._
import com.twitter.logging.Logger
import com.twitter.logging.config._
import com.twitter.util.{Config, Eval}

object RuntimeEnvironment {
  /**
   * Use the given class to find the `build.properties` file, parse any
   * command-line arguments, and return the resulting RuntimeEnvironment.
   */
  def apply(obj: AnyRef, args: Array[String]) = {
    val runtime = new RuntimeEnvironment(obj)
    runtime.parseArgs(args.toList)
    runtime
  }
}

/**
 * Use information in a local `build.properties` file to determine runtime
 * environment info like the package name, version, and installation path.
 * This can be used to automatically load config files from a `config/` path
 * relative to the executable jar.
 *
 * An example of how to generate a `build.properties` file is included in
 * sbt standard-project: <http://github.com/twitter/standard-project>
 *
 * You have to pass in an object from your package in order to identify the
 * location of the `build.properties` file.
 */
class RuntimeEnvironment(obj: AnyRef) {
  private val buildProperties = new Properties
  /** the directory in which we'll try to compile configs */
  private var configTarget: Option[String] = Some("target")
  var arguments: Map[String, String] = Map()

  try {
    buildProperties.load(obj.getClass.getResource("build.properties").openStream)
  } catch {
    case _ =>
  }

  val jarName = buildProperties.getProperty("name", "unknown")
  val jarVersion = buildProperties.getProperty("version", "0.0")
  val jarBuild = buildProperties.getProperty("build_name", "unknown")
  val jarBuildRevision = buildProperties.getProperty("build_revision", "unknown")
  val stageName = System.getProperty("stage", "production")

  // require standard-project >= 0.12.4:
  val jarBuildBranchName = buildProperties.getProperty("build_branch_name", "unknown")
  val jarBuildLastFewCommits = buildProperties.getProperty("build_last_few_commits", "unknown")

  /**
   * Return the path this jar was executed from. Depends on the presence of
   * a valid `build.properties` file. Will return `None` if it couldn't
   * figure out the environment.
   */
  lazy val jarPath: Option[String] = {
    val paths = System.getProperty("java.class.path").split(System.getProperty("path.separator"))
    findCandidateJar(paths, jarName, jarVersion).flatMap { path =>
      val file = new File(path);
      var parent = file.getParentFile
      if (parent == null) parent = file.getAbsoluteFile.getParentFile
      if (parent == null) None else Some(parent.getCanonicalPath)
    }
  }

  def findCandidateJar(paths: Seq[String], name: String, version: String): Option[String] = {
    val pattern = ("(.*?)" + name + "(?:_[\\d.]+)?-" + version + "\\.jar$").r
    paths.find { path =>
      pattern.findFirstIn(path).isDefined
    }
  }

  /**
   * Config file, as determined from this jar's runtime path, possibly
   * overridden by a command-line option.
   */
  var configFile: File = jarPath map { path =>
    new File(path + "/config/" + stageName + ".scala")
  } orElse {
    val file = new File("./config/" + stageName + ".scala")
    if (file.exists) Some(file.getAbsoluteFile) else None
  } getOrElse {
    new File("/etc/" + jarName + ".conf")
  }

  /**
   * Perform baseline command-line argument parsing.
   */
  def parseArgs(args: List[String]): Unit = {
    args match {
      case "-D" :: arg :: xs =>
        val split = arg.split("=")
        parseSetting(split(0), split(1))
        parseArgs(xs)
      case "-f" :: filename :: xs =>
        configFile = new File(filename)
        parseArgs(xs)
      case "--no-config-cache" :: xs =>
        configTarget = None
        parseArgs(xs)
      case "--config-target" :: filename :: xs =>
        configTarget = Some(filename)
        parseArgs(xs)
      case "--help" :: xs =>
        help()
      case "--version" :: xs =>
        println("%s %s %s %s".format(jarName, jarVersion, jarBuild, jarBuildRevision))
        System.exit(0)
      case "--validate" :: xs =>
        validate()
      case Nil =>
      case unknown :: _ =>
        println("Unknown command-line option: " + unknown)
        help
    }
  }

  def parseSetting(arg: String, value: String) {
    arguments = arguments + (arg -> value)
    System.setProperty(arg, value)
  }

  private def help() {
    println
    println("%s %s (%s)".format(jarName, jarVersion, jarBuild))
    println("options:")
    println("    -f <path>")
    println("        path of config files (default: %s)".format(configFile))
    println("    --no-config-cache")
    println("        don't compile configs to disk (recompile on every process start)")
    println("    --config-target <path>")
    println("        use the specified path as the target for config compilation")
    println("        note: this is relative to the directory containing the config file")
    println("    -D <key>=<value>")
    println("        set or override an optional setting")
    println("    --version")
    println("        show version information")
    println("    --validate")
    println("        validate that the config file will compile")
    println
    System.exit(0)
  }

  private def getConfigTarget(): Option[File] = {
    configTarget flatMap { fileName =>
      // if we have a config file, try to make the target dir a subdirectory
      // of the directory the config file lives in (e.g. <my-app>/config/target)
      val targetFile = if (configFile.exists && configFile.getParentFile != null) {
        new File(configFile.getParentFile, fileName)
      } else {
        new File(fileName)
      }

      // make sure we can get an actual directory, otherwise fail and return None
      if (!targetFile.exists) {
        if (targetFile.mkdirs()) {
          Some(targetFile)
        } else {
          Logger.get("").warning("couldn't make directory %s. will not cache configs", targetFile)
          None
        }
      } else if (!targetFile.isDirectory) {
        throw new IllegalArgumentException("specified target directory %s exists and is not a directory".
                                           format(fileName))
        None
      } else {
        Some(targetFile)
      }
    }
  }

  /**
   * If we don't have any loggers configured, try to get at least console output setup. In all
   * likelihood the eval'd config is going to set this to something more robust, but we at least
   * need to see errors encountered while processing the config. We also may need to rebuild this
   * if a config file threw an exception before getting around to setting up logging.
   */
  def initLogs() {
    if ((Logger.get("").getHandlers eq null) || Logger.get("").getHandlers.length == 0) {
      Logger.reset()
    }
  }

  private def validate() {
    initLogs()
    try {
      val eval = new Eval(getConfigTarget)
      val config = eval[Config[_]](configFile)
      config.validate()
      println("Config file %s compiles. :)".format(configFile))
      System.exit(0)
    } catch {
      case e: Eval.CompilerException =>
        println("Error in config file %s:".format(configFile))
        println(e.messages.flatten.mkString("\n"))
        System.exit(1)
      case e: Config.RequiredValuesMissing =>
        println("Required values missing in config file %s:".format(configFile))
        println(e.getMessage)
        System.exit(1)
    }
  }

  def loadConfig[T](): T = {
    initLogs()
    try {
      val eval = new Eval(getConfigTarget)
      val config = eval[Config[T]](configFile)
      config.validate()
      config()
    } catch {
      case e: Eval.CompilerException =>
        initLogs()
        Logger.get("").fatal(e, "Error in config file: %s", configFile)
        Logger.get("").fatal(e.messages.flatten.mkString("\n"))
        System.exit(1)
        throw new Exception("which will never execute because of the System.exit above me.")
      case e: Config.RequiredValuesMissing =>
        initLogs()
        Logger.get("").fatal("Required values missing in config file %s:".format(configFile))
        Logger.get("").fatal(e.getMessage)
        System.exit(1)
        throw new Exception("which will never execute because of the System.exit above me.")
      case e =>
        initLogs()
        Logger.get("").fatal(e, "Error in config file: %s", configFile)
        System.exit(1)
        throw new Exception("which will never execute because of the System.exit above me.")
    }
  }

  def loadRuntimeConfig[T](): T = {
    try {
      loadConfig[RuntimeEnvironment => T]()(this)
    } catch {
      case e =>
        initLogs()
        Logger.get("").fatal(e, "Error in config file: %s", configFile)
        System.exit(1)
        throw new Exception("which will never execute because of the System.exit above me.")
    }
  }
}
