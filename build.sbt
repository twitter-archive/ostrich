name := "ostrich"

version := "9.0.6"

organization := "com.twitter"

scalaVersion := "2.9.2"

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")

javacOptions in doc := Seq("-source", "1.6")

parallelExecution in Test := false

resolvers += "twitter repo" at "http://maven.twttr.com"

libraryDependencies ++= Seq(
  "com.twitter" %% "util-core" % "6.0.6",
  "com.twitter" %% "util-eval" % "6.0.6",
  "com.twitter" %% "util-logging" % "6.0.6",
  "com.twitter" %% "util-jvm" % "6.0.6",
  "com.twitter" % "scala-json" % "3.0.1"
)

libraryDependencies ++= Seq(
  "org.scala-tools.testing" % "specs_2.9.1" % "1.6.9" % "test",
  "junit" % "junit" % "4.8.1" % "test",
  "cglib" % "cglib" % "2.1_3" % "test",
  "asm" % "asm" % "1.5.3" % "test",
  "org.objenesis" % "objenesis" % "1.1" % "test",
  "org.hamcrest" % "hamcrest-all" % "1.1" % "test",
  "org.jmock" % "jmock" % "2.4.0" % "test"
)

publishMavenStyle := true

publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("sonatype-snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("sonatype-releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { x => false }

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
