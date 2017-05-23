val sbtwhitesource = project in file(".")

organization := "com.lightbend"
        name := "sbt-whitesource"
     version := "0.1.0"
    licenses := Seq(("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0")))
 description := "An sbt plugin to keep your WhiteSource project up to date"
  developers := List(Developer("dwijnand", "Dale Wijnand", "dale wijnand gmail com", url("https://dwijnand.com")))
   startYear := Some(2017)
    homepage := scmInfo.value map (_.browseUrl)
     scmInfo := Some(ScmInfo(url("https://github.com/lightbend/sbt-whitesource"), "scm:git:git@github.com:lightbend/sbt-whitesource.git"))

   sbtPlugin := true
scalaVersion := "2.10.6"

scalacOptions ++= Seq("-encoding", "utf8")
scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlint")
scalacOptions  += "-Xfuture"
scalacOptions  += "-Yno-adapted-args"
scalacOptions  += "-Ywarn-dead-code"
scalacOptions  += "-Ywarn-numeric-widen"
scalacOptions  += "-Ywarn-value-discard"

libraryDependencies += "org.whitesource" % "wss-agent-api"        % "2.3.3"
libraryDependencies += "org.whitesource" % "wss-agent-api-client" % "2.3.3"
libraryDependencies += "org.whitesource" % "wss-agent-report"     % "2.3.3"

bintrayOrganization := Some("typesafe")
bintrayRepository   := "internal-maven-releases"

cancelable in Global := true
