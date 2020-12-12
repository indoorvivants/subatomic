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

package docs

import subatomic._

sealed trait Content extends Product with Serializable
case class Doc(
    title: String,
    path: os.Path,
    dependencies: Set[String] = Set.empty
) extends Content

object Content {
  def apply(root: os.Path): Vector[(SitePath, Content)] = {
    Vector(
      SiteRoot / "index.html" -> Doc(
        "Home ",
        root / "pages" / "index.md"
      ),
      SiteRoot / "example.html" -> Doc(
        "Example",
        root / "pages" / "example.md"
      )
    )
  }
}
