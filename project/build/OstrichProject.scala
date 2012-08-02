import java.io.FileWriter
import java.util.Properties
import sbt._
import com.twitter.sbt._

class OstrichProject(info: ProjectInfo) extends StandardLibraryProject(info)
  with SubversionPublisher
  with DefaultRepos
  with PublishSourcesAndJavadocs
  with PublishSite
{

  val utilCore = "com.twitter" % "util-core" % "5.3.6"
  val utilEval = "com.twitter" % "util-eval" % "5.3.6"
  val utilLogging = "com.twitter" % "util-logging" % "5.3.6"
  val utilJvm = "com.twitter" % "util-jvm" % "5.3.6"
  val json = "com.twitter" % "scala-json" % "3.0.0"

  // for tests:
  val specs = buildScalaVersion match {
    case "2.8.1" => "org.scala-tools.testing" % "specs_2.8.1" % "1.6.6" % "test"
    case "2.9.2" => "org.scala-tools.testing" % "specs_2.9.2" % "1.6.9" % "test"
  }
  val junit = "junit" % "junit" % "4.8.1" % "test"
  val cglib = "cglib" % "cglib" % "2.1_3" % "test"
  val asm = "asm" % "asm" % "1.5.3" % "test"
  val objenesis = "org.objenesis" % "objenesis" % "1.1" % "test"
  val hamcrest = "org.hamcrest" % "hamcrest-all" % "1.1" % "test"
  val jmock = "org.jmock" % "jmock" % "2.4.0" % "test"

  override def pomExtra =
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
      </license>
    </licenses>

  // use "ostrich_<scalaversion>" as the published name:
  //override def disableCrossPaths = false

  def ostrichPropertiesPath = (mainResourcesOutputPath ##) / "ostrich.properties"
  lazy val makeOstrichProperties = task {
    val properties = new Properties
    properties.setProperty("version", version.toString)
    properties.setProperty("asu", (System.nanoTime >> 16 & 0xfff).toString)
    val fileWriter = new FileWriter(ostrichPropertiesPath.asFile)
    properties.store(fileWriter, "")
    fileWriter.close()
    None
  }
  override def copyResourcesAction = super.copyResourcesAction && makeOstrichProperties
  override def packagePaths = super.packagePaths --- (mainResourcesPath / "ostrich.properties") +++ ostrichPropertiesPath

  override def subversionRepository = Some("https://svn.twitter.biz/maven-public")

  // We depend on the stuff in the scripts directory to run tests in an
  // invocation-path independent manner.
  override def testResources =
    descendents(testResourcesPath ##, "*") +++
    descendents(sourcePath / "scripts" ##, "*")
}
