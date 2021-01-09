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
package builders

case class MdocConfig(
    dependencies: List[String]
)

object MdocConfig {
  def from(attrs: Discover.YamlAttributes): Option[MdocConfig] = {
    val enabled      = attrs.optionalOne("scala-mdoc").getOrElse("false").toBoolean
    val dependencies = attrs.optionalOne("scala-mdoc-dependencies").map(_.split(",").toList).getOrElse(Nil)

    if (enabled) Some(MdocConfig(dependencies)) else None
  }
}
