/*
 * Copyright 2020 Anton Sviridov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package subatomic

import subatomic.internal.BuildInfo

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
