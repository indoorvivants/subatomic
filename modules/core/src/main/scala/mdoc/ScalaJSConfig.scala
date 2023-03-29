package subatomic

import java.util.Properties
import subatomic.internal.BuildInfo
import coursier._
import coursier.parse.DependencyParser
import os.ProcessOutput

case class ScalaJSConfig(
    version: String,
    domVersion: String
)

object ScalaJSConfig {
  def default = ScalaJSConfig(version = "1.13.0", "2.4.0")

  def fromAttrs(attrs: Discover.YamlAttributes): Option[ScalaJSConfig] = {
    val enabled = attrs.optionalOne("mdoc-js").map(_.toBoolean).getOrElse(false)
    val version = attrs
      .optionalOne("mdoc-js-scalajs")
      .map(_.trim)
      .getOrElse(default.version)
    val domVersion = attrs
      .optionalOne("mdoc-js-dom-version")
      .map(_.trim)
      .getOrElse(default.domVersion)

    if (enabled) Some(ScalaJSConfig(version, domVersion)) else None
  }
}
