import sbt._

class OstrichProject(info: ProjectInfo) extends DefaultProject(info) {
  // Maven repositories
  val scalaToolsTesting = "testing.scala-tools.org" at "http://scala-tools.org/repo-releases/"
  val powerMock = "powermock-api" at "http://powermock.googlecode.com/svn/repo/"
  val mavenDotOrg = "repo1" at "http://repo1.maven.org/maven2/"
  val scalaToolsReleases = "scala-tools.org" at "http://scala-tools.org/repo-releases/"
  val reucon = "reucon" at "http://maven.reucon.com/public/"
  val lagDotNet = "lag.net" at "http://www.lag.net/repo/"
  val oauthDotNet = "oauth.net" at "http://oauth.googlecode.com/svn/code/maven"
  val javaDotNet = "download.java.net" at "http://download.java.net/maven/2/"
  val jBoss = "jboss-repo" at "http://repository.jboss.org/maven2/"
  val nest = "nest" at "http://www.lag.net/nest/"
  val atlassian = "atlassian" at "https://m2proxy.atlassian.com/repository/public/"

  // library dependencies
  // note that JARs in lib/ are also pulled in, and so are not mentioned here
  val vscaladoc = "org.scala-tools" % "vscaladoc" % "1.1-md-3"
  val twitterJson = "com.twitter" % "json" % "1.1"
  val configgy = "net.lag" % "configgy" % "1.4.7"
  val commonsLogging = "commons-logging" % "commons-logging" % "1.1"
  val commonsLang = "commons-lang" % "commons-lang" % "2.2"
  val mockito = "org.mockito" % "mockito-core" % "1.8.1"
  val hamcrest = "org.hamcrest" % "hamcrest-all" % "1.1"
  val xrayspecs = "com.twitter" % "xrayspecs" % "1.0.7"
  val cglib = "cglib" % "cglib" % "2.1_3"
  val asm = "asm" % "asm" % "1.5.3"
  val objenesis = "org.objenesis" % "objenesis" % "1.1"
  val jmock = "org.jmock" % "jmock" % "2.4.0"
}
