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

package subatomic.builders.blog

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import subatomic.Linker
import subatomic.SiteRoot
import subatomic.builders._

trait Template {

  def site: Blog
  def linker: Linker
  def tagPages: Seq[TagPage]

  import scalatags.Text.all._
  import scalatags.Text.TypedTag

  def Nav(navigation: Vector[NavLink]) = {
    div(
      navigation.map {
        case NavLink(_, title, selected) if selected =>
          p(strong(title))
        case NavLink(url, title, _) =>
          p(a(href := url, title))
      }
    )
  }

  def rawHtml(rawHtml: String) = div(raw(rawHtml))

  import SyntaxHighlighting._

  def highlightingHeader(sh: SyntaxHighlighting) =
    sh match {
      case hljs: HighlightJS => HighlightJS.templateBlock(hljs)
      case pjs: PrismJS      => PrismJS.includes(pjs).styles
    }

  def highlightingBody(sh: SyntaxHighlighting): Seq[TypedTag[String]] =
    sh match {
      case pjs: PrismJS => PrismJS.includes(pjs).bodyScripts
      case _            => Seq.empty
    }

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
          StylesheetPath(SiteRoot / "assets" / "subatomic-search.css"),
          StylesheetPath(SiteRoot / "assets" / "subatomic-search.css")
        )
      else Nil

    BuilderTemplate.managedStylesBlock(linker, paths)
  }

  private def templateStyles = {
    val paths = List(StylesheetPath(SiteRoot / "assets" / "template.css"))

    BuilderTemplate.managedStylesBlock(linker, paths)
  }

  def basePage(
      navigation: Option[Vector[NavLink]],
      headings: Option[Vector[Heading]],
      content: TypedTag[_]
  ) = {
    val pageTitle = navigation
      .flatMap(_.find(_.selected))
      .map(_.title)
      .map(": " + _)
      .getOrElse("")

    html(
      lang := "en",
      head(
        scalatags.Text.tags2.title(site.name + pageTitle),
        highlightingHeader(site.highlighting),
        BuilderTemplate.managedStylesBlock(linker, site.managedStyles),
        BuilderTemplate.managedScriptsBlock(linker, site.managedScripts),
        searchScripts,
        searchStyles,
        templateStyles,
        meta(charset := "UTF-8"),
        meta(
          name            := "viewport",
          attr("content") := "width=device-width, initial-scale=1"
        )
      ),
      body(
        div(
          cls := "wrapper",
          aside(
            cls := "sidebar",
            blogTitleSection,
            about,
            staticNav,
            searchSection,
            tagCloud,
            archiveLink,
            navigationSection(navigation),
            headingsSection(headings)
          ),
          article(cls := "content-wrapper", content)
        ),
        highlightingBody(site.highlighting),
        site.trackers.flatMap(_.scripts)
      )
    )
  }

  def archiveLink = {
    section(
      h4(a(href := linker.unsafe(_ / "archive.html"), "Archive"))
    )
  }

  private def navigationSection(navigation: Option[Vector[NavLink]]) =
    navigation match {
      case Some(value) =>
        section(
          cls := "site-navigation-posts",
          h4("posts"),
          Nav(value)
        )
      case None => span()
    }

  private def headingsSection(headings: Option[Vector[Heading]]) =
    headings match {
      case None => span()
      case Some(value) =>
        div(
          style := "position: sticky; position: -webkit-sticky; top: 0",
          h4("contents"),
          value.filter(_.level <= 3).map { hd =>
            span(
              raw("&nbsp;&nbsp;" * (hd.level - 1)),
              a(href := hd.url, cls := "heading-link", small(hd.title)),
              br
            )
          }
        )
    }

  private def blogTitleSection =
    section(
      cls := "site-title",
      h2(a(href := linker.root, site.name))
    )

  private def searchSection =
    section(
      cls := "site-search",
      div(id := "searchContainer", cls := "searchContainer")
    )

  def page(
      navigation: Vector[NavLink],
      headings: Option[Vector[Heading]],
      content: TypedTag[_]
  ) =
    basePage(Some(navigation), headings, content)

  def post(
      navigation: Vector[NavLink],
      headings: Vector[Heading],
      title: String,
      tags: Seq[String],
      content: String
  ): String = post(navigation, headings, title, tags, rawHtml(content))

  def post(
      navigation: Vector[NavLink],
      headings: Vector[Heading],
      title: String,
      tags: Seq[String],
      content: TypedTag[_]
  ) = {
    val tagline = tags.toList.map { tag =>
      span(
        cls := "blog-tag",
        a(href := linker.unsafe(_ / "tags" / s"$tag.html"), small(tag))
      )
    }
    "<!DOCTYPE html>" + page(
      navigation,
      Some(headings),
      div(h2(cls := "blog-post-title", title), p(tagline), hr, content)
    ).render
  }

  def tagPage(
      navigation: Vector[NavLink],
      tag: String,
      blogs: Seq[Post]
  ) = {
    page(
      navigation,
      None,
      div(
        h3(span("Posts tagged with ", span(cls := "blog-tag", tag))),
        div(cls := "card-columns", blogs.map(blogCard).toVector)
      )
    )
  }

  def dateFormat(dt: LocalDate) = dt.format(DateTimeFormatter.ISO_DATE)

  def blogPostSummary(
      title: String,
      date: LocalDate,
      url: String
  ) = {
    li(h3(a(href := url, title)), dateFormat(date))
  }

  def tagCloud = {
    section(
      cls := "site-tag-cloud",
      h4("tags"),
      nav(
        tagPages.toList.map { tagPage =>
          span(a(href := linker.find(tagPage), small(tagPage.tag)), " ")
        }
      )
    )
  }

  def blogCard(
      blogPost: Post
  ) = {
    div(
      cls := "blog-card",
      div(
        cls := "blog-card-body",
        div(
          cls := "blog-card-title",
          a(href := linker.find(blogPost), blogPost.title),
          " ",
          small(i(blogPost.date.format(DateTimeFormatter.ISO_LOCAL_DATE)))
        ),
        p(cls := "blog-card-text", blogPost.description)
      )
    )
  }

  def indexPage(
      title: String,
      blogs: Seq[Post]
  ) = {
    basePage(
      None,
      None,
      div(
        h3(title),
        div(cls := "card-columns", blogs.sorted.reverse.map(blogCard).toVector)
      )
    )
  }

  def indexPage(
      blogs: Seq[Post]
  ) = {
    basePage(
      None,
      None,
      div(
        div(cls := "card-columns", blogs.sorted.reverse.map(blogCard).toVector)
      )
    )
  }

  def archivePage(
      blogs: Seq[Post]
  ) = {
    basePage(
      None,
      None,
      div(
        h3("Archive"),
        div(cls := "card-columns", blogs.sorted.reverse.map(blogCard).toVector)
      )
    )
  }

  val section = tag("section")
  val aside   = tag("aside")
  val nav     = tag("nav")
  val article = tag("article")

  def about =
    section(
      cls := "site-tagline",
      p(site.tagline)
    )

  def staticNav =
    section(
      cls := "site-links",
      ul(
        site.links.map { case (title, url) =>
          li(
            a(
              href := url,
              title
            )
          )
        }
      )
    )
}
