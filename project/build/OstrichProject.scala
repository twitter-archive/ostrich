import sbt._
import com.twitter.sbt._

class OstrichProject(info: ProjectInfo) extends StandardLibraryProject(info) with SubversionPublisher with DefaultRepos {
  val util = "com.twitter" % "util" % "1.4.12-SNAPSHOT"
  val json = "com.twitter" % "json_2.8.1" % "2.1.6"
  val scalaCompiler = "org.scala-lang" % "scala-compiler" % "2.8.1" % "compile"
  val netty = "org.jboss.netty" % "netty" % "3.2.3.Final"
  val commonsLogging = "commons-logging" % "commons-logging" % "1.1"
  val commonsLang = "commons-lang" % "commons-lang" % "2.2"

  // for tests:
  val specs = "org.scala-tools.testing" % "specs_2.8.1" % "1.6.6" % "test"
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
