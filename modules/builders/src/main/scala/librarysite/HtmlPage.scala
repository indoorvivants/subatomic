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

  private def templateStyles = {
    val paths = List(StylesheetPath(SiteRoot / "assets" / "styles.css"))

    BuilderTemplate.managedStylesBlock(linker, paths)
  }

  def doc(
      title: String,
      content: String,
      toc: Option[TOC],
      links: LibrarySite.NavTree
  ): String =
    doc(title, RawHTML(content), toc, links)

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

  // private def whoosh(t: Theme => WithClassname) =
  //   t(theme).className.map(cls := _)

  def doc(
      title: String,
      content: TypedTag[_],
      toc: Option[TOC],
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
        meta(charset := "UTF-8"),
        meta(
          name            := "viewport",
          attr("content") := "width=device-width, initial-scale=1"
        )
      ),
      body(
        onclick := "SubatomicSearchFrontend.sayHello()",
        cls     := "sb-body",
        Header,
        tag("main")(
          cls := "sb-main-container",
          tag("aside")(cls := "sb-aside", NavigationBar(links)),
          tag("article")(
            cls := "sb-article",
            toc.map(Html.renderTOC(_)),
            content
          )
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
        cls := "sb-navbar-container",
        cls := s"sb-navbar-container-${nt.depth}",
        nt.level.map { case (doc, sub, expanded) =>
          li(
            cls := "sb-navbar-link-container",
            a(
              cls := "sb-navbar-link",
              cls := s"sb-navbar-link-${nt.depth}",
              // TODO
              // Option.when(expanded)(
              //   cls := "sb-navbar-link-expanded",
              //   cls := s"sb-navbar-link-expanded-${nt.depth}"
              // ).toList,
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
      cls := "sb-header",
      div(
        cls := "sb-title-container",
        a(cls := "sb-title-link", href := linker.root, site.name),
        site.tagline.map { tagline => p(cls := "sb-title-tagline", tagline) }
      ),
      div(id := "sb-search-container"),
      div(
        cls := "sb-site-links",
        site.githubUrl.map { githubUrl =>
          a(
            cls  := "sb-github-link",
            href := githubUrl,
            img(
              cls := "sb-github-image",
              src := "https://cdn.svgporn.com/logos/github-icon.svg"
            )
          )
        }
      )
    )

  def Footer =
    footer(cls := "sb-footer", site.copyright)
}
