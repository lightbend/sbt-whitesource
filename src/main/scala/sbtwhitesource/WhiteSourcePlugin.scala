package sbtwhitesource

import sbt._, Keys._

object WhiteSourcePlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger  = allRequirements

  object autoImport {
  }
  import autoImport._

  override def globalSettings  = Seq()
  override def buildSettings   = Seq()
  override def projectSettings = Seq()
}
