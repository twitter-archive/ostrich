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

package com.twitter.admin

import java.io.File
import java.util.Properties
import scala.collection.mutable
import com.twitter.config.Config
import com.twitter.conversions.string._
import com.twitter.logging.Logger
import com.twitter.util.Eval

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
 * sbt stanard-project: <http://github.com/twitter/standard-project>
 *
 * You have to pass in an object from your package in order to identify the
 * location of the `build.properties` file.
 */
class RuntimeEnvironment(obj: AnyRef) {
  private val buildProperties = new Properties

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

  /**
   * Return the path this jar was executed from. Depends on the presence of
   * a valid `build.properties` file. Will return `None` if it couldn't
   * figure out the environment.
   */
  lazy val jarPath: Option[String] = {
    val paths = System.getProperty("java.class.path").split(System.getProperty("path.separator"))
    findCandidateJar(paths, jarName, jarVersion).flatMap { path =>
      val parent = new File(path).getParentFile
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
   * Config path, as determined from this jar's runtime path, possibly
   * overridden by a command-line option.
   */
  var configPath: File = jarPath match {
    case Some(path) => new File(path + "/config/" + stageName)
    case None => new File("/etc/" + jarName)
  }

  def loggingConfigFile: File = new File(configPath, "logging.scala")

  def configFile: File = new File(configPath, jarName + ".scala")

  /**
   * Perform baseline command-line argument parsing. Responds to `--help`,
   * `--version`, and `-f` (which overrides the config filename).
   */
  def parseArgs(args: List[String]): Unit = {
    args match {
      case "-f" :: filename :: xs =>
        configPath = new File(filename)
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

  private def help() {
    println
    println("%s %s (%s)".format(jarName, jarVersion, jarBuild))
    println("options:")
    println("    -f <path>")
    println("        path of config files (default: %s)".format(configPath))
    println("    --version")
    println("        show version information")
    println("    --validate")
    println("        validate that the config file will compile")
    println
    System.exit(0)
  }

  private def validate() {
    try {
      Eval[Any](configFile)
      println("Config file %s compiles. :)".format(configFile))
      System.exit(0)
    } catch {
      case e: Eval.CompilerException =>
        println("Error in config file %s:".format(configFile))
        println(e.messages.flatten.mkString("\n"))
        System.exit(1)
    }
  }

  def loadConfig[T](): T = {
    try {
      Eval[Config[T]](configFile)()
    } catch {
      case e: Eval.CompilerException =>
        Logger.get("").fatal(e, "Error in config file: %s", configFile)
        Logger.get("").fatal(e.messages.flatten.mkString("\n"))
        System.exit(1)
        throw new Exception("which will never execute because of the System.exit above me.")
    }
  }

  def loadRuntimeConfig[T](): T = {
    loadConfig[RuntimeEnvironment => T]()(this)
  }
}
