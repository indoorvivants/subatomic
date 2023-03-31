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

package subatomic.builders.librarysite

import subatomic._
import subatomic.builders._

trait HtmlPage {
  def site: LibrarySite
  def linker: Linker
  def theme: Theme

  import scalatags.Text.all._
  import scalatags.Text.TypedTag

  def RawHTML(rawHtml: String) = div(raw(rawHtml))

  private def searchScripts = {
    val paths =
      if (site.search)
        List(
          ScriptPath(SiteRoot / "assets" / "search.js"),
          ScriptPath(SiteRoot / "assets" / "search-index.js")
        )
      else Nil

    BuilderTemplate.managedScriptsBlock(linker, paths)
  }

  private def searchStyles = {
    val paths =
      if (site.search)
        List(
          StylesheetPath(SiteRoot / "assets" / "subatomic-search.css")
        )
      else Nil

    BuilderTemplate.managedStylesBlock(linker, paths)
  }

  private def templateStyles = {
    val paths = List(StylesheetPath(SiteRoot / "assets" / "tailwind.css"))

    BuilderTemplate.managedStylesBlock(linker, paths)
  }

  def doc(title: String, content: String, links: LibrarySite.NavTree): String =
    doc(title, RawHTML(content), links)

  import SyntaxHighlighting._

  def highlightingHeader(sh: SyntaxHighlighting) =
    sh match {
      case hljs: HighlightJS => HighlightJS.templateBlock(hljs)

      case pjs: PrismJS => PrismJS.includes(pjs).styles
    }

  def highlightingBody(sh: SyntaxHighlighting) =
    sh match {
      case pjs: PrismJS => PrismJS.includes(pjs).bodyScripts
      case _            => Seq.empty
    }

  private def whoosh(t: Theme => WithClassname) =
    t(theme).className.map(cls := _)

  def doc(
      title: String,
      content: TypedTag[_],
      links: LibrarySite.NavTree
  ): String = {
    html(
      head(
        scalatags.Text.tags2.title(s"${site.name}: $title"),
        highlightingHeader(site.highlighting),
        BuilderTemplate.managedScriptsBlock(linker, site.managedScripts),
        BuilderTemplate.managedStylesBlock(linker, site.managedStyles),
        templateStyles,
        searchScripts,
        searchStyles,
        meta(charset := "UTF-8"),
        meta(
          name            := "viewport",
          attr("content") := "width=device-width, initial-scale=1"
        )
      ),
      body(
        onclick := "SubatomicSearchFrontend.sayHello()",
        whoosh(_.Body),
        Header,
        tag("main")(
          whoosh(_.Container),
          tag("aside")(whoosh(_.Aside), NavigationBar(links)),
          tag("article")(whoosh(_.Main), cls := "markdown", content)
        ),
        Footer,
        highlightingBody(site.highlighting),
        site.trackers.flatMap(_.scripts)
      )
    ).render
  }

  def NavigationBar(levels: LibrarySite.NavTree) = {
    def rend(nt: LibrarySite.NavTree): TypedTag[String] = {
      ul(
        whoosh(_.Navigation.Container(nt.depth)),
        nt.level.map { case (doc, sub, expanded) =>
          li(
            a(
              whoosh(_.Navigation.Link(nt.depth, expanded)),
              href := linker.find(doc),
              doc.title
            ),
            rend(sub)
          )
        }
      )
    }

    rend(levels)
  }

  def Header =
    header(
      whoosh(_.Header.Container),
      div(
        whoosh(_.Header.TitleContainer),
        a(whoosh(_.Header.Title), href := linker.root, site.name),
        site.tagline.map { tagline => p(whoosh(_.Header.Subtitle), tagline) }
      ),
      div(id := "searchContainer"),
      div(
        cls := "site-links",
        site.githubUrl.map { githubUrl =>
          a(
            href := githubUrl,
            img(
              whoosh(_.Header.GithubUrl),
              src := "https://cdn.svgporn.com/logos/github-icon.svg"
            )
          )
        }
      )
    )

  def Footer =
    footer(whoosh(_.Footer), site.copyright)
}
