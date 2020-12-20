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

case class NavLink(
    url: String,
    title: String,
    selected: Boolean
)

class Template(linker: Linker) {
  import scalatags.Text.all._
  import scalatags.Text.TypedTag

  def RawHTML(rawHtml: String) = div(raw(rawHtml))

  def doc(title: String, content: String, links: Vector[NavLink]): String =
    doc(title, RawHTML(content), links)

  def doc(title: String, content: TypedTag[_], links: Vector[NavLink]): String = {
    html(
      head(
        scalatags.Text.tags2.title(s"Subatomic: $title"),
        link(
          rel := "stylesheet",
          href := linker.unsafe(_ / "assets" / "highlight-theme.css")
        ),
        link(
          rel := "stylesheet",
          href := linker.unsafe(_ / "assets" / "styles.css")
        ),
        link(
          rel := "shortcut icon",
          `type` := "image/png",
          href := linker.unsafe(_ / "assets" / "logo.png")
        ),
        script(src := linker.unsafe(_ / "assets" / "highlight.js")),
        script(src := linker.unsafe(_ / "assets" / "highlight-scala.js")),
        script(src := linker.unsafe(_ / "assets" / "script.js")),
        script(src := linker.unsafe(_ / "assets" / "search-index.js")),
        meta(charset := "UTF-8")
      ),
      body(
        div(
          cls := "container",
          Header,
          NavigationBar(links),
          hr,
          content
        ),
        Footer,
        script(src := linker.unsafe(_ / "assets" / "search.js"))
      )
    ).render
  }

  def NavigationBar(links: Vector[NavLink]) =
    div(
      links.map { link =>
        val sel = if (link.selected) " nav-selected" else ""
        a(
          cls := "nav-btn" + sel,
          href := link.url,
          link.title
        )
      }
    )

  val Header = header(
    cls := "main-header",
    div(
      cls := "site-title",
      h1(
        a(href := linker.root, "Subatomic")
      ),
      small("a horrible static site builder library no one should use")
    ),
    div(id := "searchContainer", cls := "searchContainer"),
    div(
      cls := "site-links",
      a(
        href := "https://github.com/indoorvivants/subatomic",
        img(src := "https://cdn.svgporn.com/logos/github-icon.svg", cls := "gh-logo")
      )
    )
  )

  val Footer = footer(
    "© 2020 Anton Sviridov"
  )
}
