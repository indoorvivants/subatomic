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
