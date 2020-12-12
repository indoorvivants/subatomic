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

class Template(linker: Linker) {
  import scalatags.Text.all._
  import scalatags.Text.TypedTag

  def RawHTML(rawHtml: String) = div(raw(rawHtml))

  def main(title: String, content: String): String =
    main(title, RawHTML(content))

  def main(title: String, content: TypedTag[_]): String = {
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
          NavigationBar,
          h1(title),
          content
        ),
        Footer,
        script(src := linker.unsafe(_ / "assets" / "search.js"))
      )
    ).render
  }

  val NavigationBar = div(
    a(
      cls := "nav-btn",
      href := linker.unsafe(_ / "index.html"),
      "Home"
    ),
    a(
      cls := "nav-btn",
      href := linker.unsafe(_ / "example.html"),
      "Example"
    )
  )

  val Header = header(
    cls := "main-header",
    div(
      cls := "logo",
      img(
        src := linker.unsafe(_ / "assets" / "logo.png")
      )
    ),
    div(
      cls := "site-title",
      h1(a(href := linker.root, "Subatomic")),
      small("a tiny, horrible static site builder for Scala")
    ),
    div(id := "searchContainer"),
    div(
      cls := "site-links",
      p(
        a(
          href := "https://github.com/indoorvivants/subatomic",
          "Github"
        )
      ),
      p(
        a(
          href := "https://index.scala-lang.org/indoorvivants/subatomic/subatomic",
          "Versions"
        )
      )
    )
  )

  val Footer = div(
    cls := "footer",
    "© 2020 Anton Sviridov"
  )
}
