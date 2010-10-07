import sbt._

import com.twitter.sbt._

class OstrichProject(info: ProjectInfo) extends StandardProject(info) with SubversionPublisher {
  val specs = "org.scala-tools.testing" %% "specs" % "1.6.5"
  val vscaladoc = "org.scala-tools" % "vscaladoc" % "1.1-md-3"
  val twitterJson = "com.twitter" %% "json" % "2.1.4"
  val configgy = "net.lag" % "configgy" % "2.0.0"
  val commonsLogging = "commons-logging" % "commons-logging" % "1.1"
  val commonsLang = "commons-lang" % "commons-lang" % "2.2"
  val mockito = "org.mockito" % "mockito-core" % "1.8.4"
  val hamcrest = "org.hamcrest" % "hamcrest-all" % "1.1"
  val xrayspecs = "com.twitter" %% "xrayspecs" % "2.0"
  val cglib = "cglib" % "cglib" % "2.1_3"
  val asm = "asm" % "asm" % "1.5.3"
  val objenesis = "org.objenesis" % "objenesis" % "1.1"
  val netty = "org.jboss.netty" % "netty" % "3.1.5.GA"

  val jbossRepo = "JBoss Repository" at "http://repository.jboss.org/nexus/content/groups/public/"

  override def disableCrossPaths = false

  override def pomExtra =
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
      </license>
    </licenses>

  override def subversionRepository = Some("http://svn.local.twitter.com/maven-public")
}
