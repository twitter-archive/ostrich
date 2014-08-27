import sbt._
import Keys._
import Tests._

object Ostrich extends Build {
  val libVersion = "9.5.6"
  val utilVersion = "6.19.0"
  val jacksonVersion = "2.4.1"

  val sharedSettings = Seq(
    name := "ostrich",
    version := libVersion,
    organization := "com.twitter",
    crossScalaVersions := Seq("2.9.2", "2.10.4", "2.11.2"),
    javacOptions ++= Seq("-source", "1.6", "-target", "1.6"),
    javacOptions in doc := Seq("-source", "1.6"),
    parallelExecution in Test := false,
    resolvers += "twitter repo" at "http://maven.twttr.com",
    libraryDependencies ++= Seq(
      "com.twitter" %% "util-core" % utilVersion,
      "com.twitter" %% "util-eval" % utilVersion,
      "com.twitter" %% "util-logging" % utilVersion,
      "com.twitter" %% "util-jvm" % utilVersion,
      "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion
    ),

    libraryDependencies <+= scalatest,
    libraryDependencies ++= Seq(
      "junit" % "junit" % "4.8.1" % "test",
      "cglib" % "cglib" % "2.1_3" % "test",
      "asm" % "asm" % "1.5.3" % "test",
      "org.objenesis" % "objenesis" % "1.1" % "test",
      "org.hamcrest" % "hamcrest-all" % "1.1" % "test",
      "org.mockito" % "mockito-all" % "1.9.5" % "test"
    ),
    publishMavenStyle := true,
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("sonatype-snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("sonatype-releases"  at nexus + "service/local/staging/deploy/maven2")
    },

    publishArtifact in Test := false,

    pomIncludeRepository := { x => false },

    pomExtra := (
      <url>https://github.com/twitter/ostrich</url>
      <licenses>
        <license>
          <name>Apache 2</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
          <distribution>repo</distribution>
          <comments>A business-friendly OSS license</comments>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:twitter/ostrich.git</url>
        <connection>scm:git:git@github.com:twitter/ostrich.git</connection>
      </scm>
      <developers>
        <developer>
          <id>twitter</id>
          <name>Twitter Inc.</name>
          <url>https://www.twitter.com/</url>
        </developer>
      </developers>
    )
  )

  lazy val scalatest = scalaVersion(sv => sv match {
    case "2.9.2" => "org.scalatest" %% "scalatest" % "1.9.2" % "test"
    case _ => "org.scalatest" %% "scalatest" % "2.1.3" % "test"
  })

  lazy val ostrich = Project(
    id = "ostrich",
    base = file("."),
    settings = Project.defaultSettings ++ sharedSettings
  )
}
