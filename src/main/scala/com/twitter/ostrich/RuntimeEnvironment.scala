package com.twitter.ostrich

import java.util.Properties

class RuntimeEnvironment(clazz: Class[_]) {
  private[this] val buildProperties = new Properties

  try {
    buildProperties.load(clazz.getResource("build.properties").openStream)
  } catch {
    case _ =>
  }

  val jarName = buildProperties.getProperty("name", "unknown")
  val jarVersion = buildProperties.getProperty("version", "0.0")
  val jarBuild = buildProperties.getProperty("build_name", "unknown")
  val jarBuildRevision = buildProperties.getProperty("build_revision", "unknown")
}