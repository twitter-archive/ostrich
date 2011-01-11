import sbt._
import com.twitter.sbt._

class OstrichProject(info: ProjectInfo) extends StandardProject(info) with SubversionPublisher with InlineDependencies {
  inline("com.twitter" % "json_2.8.0" % "2.1.4")
  inline("com.twitter" % "configgy" % "3.0.0-SNAPSHOT")
  val util = "com.twitter" % "util" % "1.4.9-SNAPSHOT"
  val netty = "org.jboss.netty" % "netty" % "3.1.5.GA"
  val commonsLogging = "commons-logging" % "commons-logging" % "1.1"
  val commonsLang = "commons-lang" % "commons-lang" % "2.2"

  // for tests:
  val specs = "org.scala-tools.testing" % "specs_2.8.0" % "1.6.5" % "test"
  val cglib = "cglib" % "cglib" % "2.1_3" % "test"
  val asm = "asm" % "asm" % "1.5.3" % "test"
  val objenesis = "org.objenesis" % "objenesis" % "1.1" % "test"
  val mockito = "org.mockito" % "mockito-core" % "1.8.4" % "test"
  val hamcrest = "org.hamcrest" % "hamcrest-all" % "1.1" % "test"

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
