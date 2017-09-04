val sbtwhitesource = project in file(".")

organization := "com.lightbend"
        name := "sbt-whitesource"
    licenses := Seq(("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0")))
 description := "An sbt plugin to keep your WhiteSource project up to date"
  developers := List(Developer("dwijnand", "Dale Wijnand", "dale wijnand gmail com", url("https://dwijnand.com")))
   startYear := Some(2017)
    homepage := scmInfo.value map (_.browseUrl)
     scmInfo := Some(ScmInfo(url("https://github.com/lightbend/sbt-whitesource"), "scm:git:git@github.com:lightbend/sbt-whitesource.git"))

       sbtPlugin           := true
      sbtVersion in Global := "0.13.16" // must be Global, otherwise ^^ won't change anything
crossSbtVersions           := List("0.13.16", "1.0.0-RC3")

scalaVersion := (CrossVersion partialVersion (sbtVersion in pluginCrossBuild).value match {
  case Some((0, 13)) => "2.10.6"
  case Some((1, _))  => "2.12.3"
  case _             => sys error s"Unhandled sbt version ${(sbtVersion in pluginCrossBuild).value}"
})

scalacOptions ++= Seq("-encoding", "utf8")
scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlint")
scalacOptions  += "-Xfuture"
scalacOptions  += "-Yno-adapted-args"
scalacOptions  += "-Ywarn-dead-code"
scalacOptions  += "-Ywarn-numeric-widen"
scalacOptions  += "-Ywarn-value-discard"

libraryDependencies += Defaults.sbtPluginExtra(
  "com.dwijnand" % "sbt-compat" % "1.0.0",
  (sbtBinaryVersion in pluginCrossBuild).value,
  (scalaBinaryVersion in update).value
)
libraryDependencies += "org.whitesource" % "wss-agent-api"        % "2.3.3"
libraryDependencies += "org.whitesource" % "wss-agent-api-client" % "2.3.3"
libraryDependencies += "org.whitesource" % "wss-agent-report"     % "2.3.3"

bintrayOrganization := Some("sbt")
bintrayRepository   := "sbt-plugin-releases"

cancelable in Global := true
