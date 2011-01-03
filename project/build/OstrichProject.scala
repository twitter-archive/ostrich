import sbt._

import com.twitter.sbt._

class OstrichProject(info: ProjectInfo) extends StandardProject(info) with SubversionPublisher {
  override def subversionRepository = Some("http://svn.local.twitter.com/maven-public")
  override def disableCrossPaths = true
  override def managedStyle = ManagedStyle.Maven

  val jbossRepo = "JBoss Repository" at "http://repository.jboss.org/nexus/content/groups/public/"

  val twitterJson = "com.twitter" % "json_2.8.1" % "2.1.6"
  val configgy = "net.lag" % "configgy" % "2.0.2"
  val netty = "org.jboss.netty" % "netty" % "3.2.3.Final"
  val xrayspecs = "com.twitter" % "xrayspecs_2.8.1" % "2.1.2"

  // test-only dependencies
  val specs = "org.scala-tools.testing" % "specs_2.8.1" % "1.6.6" % "test"
  val mockito = "org.mockito" % "mockito-core" % "1.8.4" % "test"

  val vscaladoc = "org.scala-tools" % "vscaladoc" % "1.1-md-3" % "provided->default"

  // Credentials(Path.userHome / ".ivy2" / "credentials", log)
  // val publishTo = "nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"

  override def pomExtra =
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
}
