import sbt._

import com.twitter.sbt._

class OstrichProject(info: ProjectInfo) extends StandardProject(info) with SubversionPublisher {
  override def disableCrossPaths = true
  override def managedStyle = ManagedStyle.Maven

  val jbossRepo = "JBoss Repository" at "http://repository.jboss.org/nexus/content/groups/public/"

  val twitterJson = "com.twitter" % "json_2.8.0" % "2.1.4"
  val configgy = "net.lag" % "configgy" % "2.0.0"
  val commonsLogging = "commons-logging" % "commons-logging" % "1.1"
  val commonsLang = "commons-lang" % "commons-lang" % "2.2"
  val netty = "org.jboss.netty" % "netty" % "3.1.5.GA"
  val xrayspecs = "com.twitter" % "xrayspecs_2.8.0" % "2.0"

  val specs = "org.scala-tools.testing" % "specs_2.8.0" % "1.6.5" % "test"
  val mockito = "org.mockito" % "mockito-core" % "1.8.4" % "test"
  val hamcrest = "org.hamcrest" % "hamcrest-all" % "1.1" % "test"
  val cglib = "cglib" % "cglib" % "2.1_3" % "test"
  val asm = "asm" % "asm" % "1.5.3" % "test"
  val objenesis = "org.objenesis" % "objenesis" % "1.1" % "test"

  val vscaladoc = "org.scala-tools" % "vscaladoc" % "1.1-md-3" % "provided->default"

  Credentials(Path.userHome / ".ivy2" / "credentials", log)
  val publishTo = "nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"

  override def pomExtra =
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
}
