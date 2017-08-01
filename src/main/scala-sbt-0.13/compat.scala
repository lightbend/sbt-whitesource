package sbt

package librarymanagement {
  final case class ConfigRef(name: String)

  object ConfigRef {
    def wrap(s: String): ConfigRef = ConfigRef(s)
  }

  object `package` {
    implicit class RichUpdateReport(val _ur: UpdateReport) extends AnyVal {
      def configuration(c: ConfigRef) = _ur configuration c.name
    }
  }
}
