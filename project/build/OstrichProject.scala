import sbt._
import com.twitter.sbt._

class OstrichProject(info: ProjectInfo) extends StandardProject(info) with SubversionPublisher with InlineDependencies {
  inline("net.lag" % "configgy" % "[1.6,1.7[")
  inline("com.twitter" % "json" % "1.1.8")
  val commonsLogging = "commons-logging" % "commons-logging" % "1.1"
  val commonsLang = "commons-lang" % "commons-lang" % "2.2"
  val xrayspecs = "com.twitter" % "xrayspecs" % "1.0.7"
  val netty = "org.jboss.netty" % "netty" % "3.1.5.GA"

  val specs = "org.scala-tools.testing" % "specs" % "1.6.2.1" % "test"
  val mockito = "org.mockito" % "mockito-core" % "1.8.1" % "test"
  val hamcrest = "org.hamcrest" % "hamcrest-all" % "1.1" % "test"
  val cglib = "cglib" % "cglib" % "2.1_3" % "test"
  val asm = "asm" % "asm" % "1.5.3" % "test"
  val objenesis = "org.objenesis" % "objenesis" % "1.1" % "test"

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
