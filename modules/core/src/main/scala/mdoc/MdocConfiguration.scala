package subatomic

import java.util.Properties
import subatomic.internal.BuildInfo
import coursier._
import coursier.parse.DependencyParser
import os.ProcessOutput

case class MdocConfiguration(
    scalaBinaryVersion: String,
    scalaVersion: String,
    mdocVersion: String,
    inheritClasspath: Boolean,
    inheritVariables: Boolean,
    variables: Map[String, String],
    group: String,
    extraDependencies: Set[String],
    scalajsConfig: Option[ScalaJSConfig]
)

object MdocConfiguration {
  def default =
    MdocConfiguration(
      scalaBinaryVersion = BuildInfo.scalaBinaryVersion,
      scalaVersion = BuildInfo.scalaVersion,
      mdocVersion = "2.3.7",
      inheritClasspath = true,
      inheritVariables = true,
      variables = Map.empty,
      group = "default",
      extraDependencies = Set.empty,
      scalajsConfig = None
    )

  def fromAttrs(attrs: Discover.YamlAttributes): Option[MdocConfiguration] = {
    val defaultConfig = default
    val enabled       = attrs.optionalOne("mdoc").getOrElse("false").toBoolean
    val dependencies = attrs
      .optionalOne("mdoc-dependencies")
      .map(_.split(",").toList.map(_.trim).toSet)
      .getOrElse(default.extraDependencies)

    val scalaVersion = attrs
      .optionalOne("mdoc-scala")
      .map(_.trim)
      .getOrElse(defaultConfig.scalaBinaryVersion)
    val group =
      attrs.optionalOne("mdoc-group").map(_.trim).getOrElse(defaultConfig.group)
    val version = attrs
      .optionalOne("mdoc-version")
      .map(_.trim)
      .getOrElse(defaultConfig.mdocVersion)

    val scalajs = ScalaJSConfig.fromAttrs(attrs: Discover.YamlAttributes)

    val config = defaultConfig.copy(
      scalaBinaryVersion = scalaVersion,
      group = group,
      mdocVersion = version,
      extraDependencies = dependencies,
      scalajsConfig = scalajs
    )

    if (enabled) Some(config) else None
  }
}
