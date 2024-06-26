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

import subatomic.builders.Tracker

import io.lemonlabs.uri.Url

object DevBlog extends subatomic.builders.blog.Blog.App {
  import subatomic.builders.blog._
  val docsBase = os.pwd / "docs" / "blog"
  def config =
    Blog(
      contentRoot = docsBase / "content",
      base = SiteRoot / "blog",
      name = "Subatomic Blog",
      search = true,
      tagline = Some(
        "Subatomic - development blog"
      ),
      links = Vector(
        "Back to the site" -> "https://subatomic.indoorvivants.com",
        "Github"           -> "https://github.com/indoorvivants/subatomic"
      ),
      trackers = Seq(Tracker.GoogleAnalytics("G-9RNQGQHVLB")),
      rssConfig = Some(
        RSSConfig(
          description = "Subatomic development blog",
          title = "Subatomic blog"
        )
      ),
      publicUrl = Url.parse("https://subatomic.indoorvivants.com/blog/"),
      authors = List(
        Author(
          "anton",
          "Anton Sviridov",
          Map(
            "Github"  -> "https://github.com/keynmol",
            "Twitter" -> "https://twitter.com/velvetbaldmime"
          )
        ),
        Author("anton-evil-twin", "Anton Evilov")
      )
    )
}

object Docs extends subatomic.builders.librarysite.LibrarySite.App {
  import subatomic.builders.librarysite._
  override def extra(site: Site[LibrarySite.Doc]) = {
    site
      .addCopyOf(SiteRoot / "CNAME", os.pwd / "docs" / "assets" / "CNAME")
  }

  val currentYear = LocalDate.now().getYear()

  def config =
    LibrarySite(
      name = "Subatomic",
      contentRoot = os.pwd / "docs" / "pages",
      assetsRoot = Some(os.pwd / "docs" / "assets"),
      assetsFilter = _.baseName != "CNAME",
      copyright = Some(s"© 2020-$currentYear Anton Sviridov"),
      tagline = Some("Watch this space, but please don't use it yet"),
      githubUrl = Some("https://github.com/indoorvivants/subatomic"),
      trackers = Seq(Tracker.GoogleAnalytics("G-9RNQGQHVLB"))
    )
}
