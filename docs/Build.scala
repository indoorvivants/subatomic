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
package docs

import java.time.LocalDate

import subatomic.builders._
import subatomic.builders.spa.librarysite._

object Docs extends SpaLibrarySite.App {
  override def extra(site: Site[SpaLibrarySite.Doc]) = {
    site
      .addCopyOf(SiteRoot / "CNAME", os.pwd / "docs" / "assets" / "CNAME")
  }

  val currentYear = LocalDate.now().getYear()

  def config =
    SpaLibrarySite(
      name = "Subatomic",
      contentRoot = os.pwd / "docs" / "pages",
      assetsRoot = Some(os.pwd / "docs" / "assets"),
      assetsFilter = _.baseName != "CNAME",
      copyright = Some(s"© 2020-$currentYear Anton Sviridov"),
      tagline = Some("Watch this space, but please don't use it yet"),
      githubUrl = Some("https://github.com/indoorvivants/subatomic"),
      highlightJS = HighlightJS.default.copy(
        languages = List("scala"),
        theme = "monokai-sublime"
      )
    )
}
