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
import subatomic.builders.blog.themes.Theme

trait HtmlPage {

  def site: Blog
  def linker: Linker
  def tagPages: Seq[TagPage]
  def theme: Theme

  import scalatags.Text.all._
  import scalatags.Text.TypedTag

  def Nav(navigation: Vector[NavLink]) = {
    ul(
      whoosh(_.Aside.NavContainer),
      navigation.map {
        case NavLink(_, title, selected) if selected =>
          li(span(whoosh(_.Aside.NavCurrent), title))
        case NavLink(url, title, _) =>
          li(a(whoosh(_.Aside.NavLink), href := url, title))
      }
    )
  }

  def rawHtml(rawHtml: String) = raw(rawHtml)

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

  def basePage(
      navigation: Option[Vector[NavLink]],
      headings: Option[Vector[Heading]],
      content: TypedTag[_]
  ) = {
    val pageTitle = navigation
      .flatMap(_.find(_.selected)) match {
      case None => site.name
      case Some(value) =>
        value.title
    }

    html(
      lang := "en",
      head(
        scalatags.Text.tags2.title(pageTitle),
        highlightingHeader(site.highlighting),
        BuilderTemplate.managedStylesBlock(linker, site.managedStyles),
        BuilderTemplate.managedStylesBlock(
          linker,
          List(StylesheetPath(SiteRoot / "assets" / "tailwind.css"))
        ),
        BuilderTemplate.managedScriptsBlock(linker, site.managedScripts),
        searchScripts,
        meta(charset := "UTF-8"),
        meta(
          name            := "viewport",
          attr("content") := "width=device-width, initial-scale=1"
        )
      ),
      body(
        whoosh(_.Body),
        div(
          whoosh(_.Container),
          aside(
            whoosh(_.Aside.Container),
            blogTitleSection,
            staticNav,
            searchSection,
            tagCloud,
            navigationSection(navigation),
            archiveLink,
            headingsSection(headings)
          ),
          tag("main")(content)
        ),
        highlightingBody(site.highlighting),
        site.trackers.flatMap(_.scripts)
      )
    )
  }

  def archiveLink = {
    section(
      h4(
        whoosh(_.Aside.Section.TitleLink),
        a(href := linker.unsafe(_ / "archive.html"), "Archive")
      )
    )
  }

  private def whoosh(t: Theme => WithClassname) =
    t(theme).className.map(cls := _)

  private def navigationSection(navigation: Option[Vector[NavLink]]) =
    navigation match {
      case Some(value) =>
        section(
          whoosh(_.Aside.Section.Container),
          h4(whoosh(_.Aside.Section.Title), "posts"),
          nav(
            whoosh(_.Aside.Section.Content),
            Nav(value)
          )
        )
      case None => span()
    }

  private def headingsSection(headings: Option[Vector[Heading]]) =
    headings match {
      case None => span()
      case Some(value) =>
        section(
          whoosh(_.Aside.Section.Container),
          style := "position: sticky; position: -webkit-sticky; top: 0",
          h4(whoosh(_.Aside.Section.Title), "contents"),
          value.filter(_.level <= 3).map { hd =>
            span(
              raw("&nbsp;&nbsp;" * (hd.level - 1)),
              a(href := hd.url, small(hd.title)),
              br
            )
          }
        )
    }

  private def blogTitleSection =
    div(
      whoosh(_.Logo.Container),
      a(whoosh(_.Logo.Title), href := linker.root, site.name),
      about
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
      toc: Option[TOC],
      content: String
  ): String = post(
    navigation,
    headings,
    title,
    tags,
    toc,
    article(
      whoosh(_.Post.Container),
      cls := "markdown",
      toc.map(Html.renderTOC(_, theme.Markdown)),
      rawHtml(content)
    )
  )

  def post(
      navigation: Vector[NavLink],
      headings: Vector[Heading],
      title: String,
      tags: Seq[String],
      toc: Option[TOC],
      content: TypedTag[_]
  ) = {
    val tagline = tags.toList.map { tag =>
      a(
        whoosh(_.Tag),
        href := linker.unsafe(_ / "tags" / s"$tag.html"),
        tag
      )
    }
    "<!DOCTYPE html>" + page(
      navigation,
      Some(headings),
      div(
        h2(whoosh(_.Post.Title), title),
        p(whoosh(_.Post.Description), tagline),
        content
      )
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
        h3(
          whoosh(_.TagPage.Header),
          "Posts tagged with ",
          span(whoosh(_.Tag), tag)
        ),
        div(blogs.map(blogCard).toVector)
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
      whoosh(_.Aside.Section.Container),
      h4(whoosh(_.Aside.Section.Title), "tags"),
      nav(
        whoosh(_.Aside.Section.Content),
        whoosh(_.TagCloud.Container),
        tagPages.toList.map { tagPage =>
          a(
            whoosh(_.TagCloud.Tag),
            href := linker.find(tagPage),
            small(tagPage.tag)
          )
        }
      )
    )
  }

  def blogCard(
      blogPost: Post
  ) = {
    section(
      whoosh(_.PostCard.Container),
      div(
        whoosh(_.PostCard.Body),
        div(
          a(
            whoosh(_.PostCard.Title),
            href := linker.find(blogPost),
            blogPost.title
          ),
          span(
            whoosh(_.PostCard.Date),
            blogPost.date.format(DateTimeFormatter.ISO_LOCAL_DATE)
          )
        ),
        p(whoosh(_.PostCard.Description), blogPost.description)
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
        h3(whoosh(_.ArchivePage.Header), "Archive"),
        div(cls := "card-columns", blogs.sorted.reverse.map(blogCard).toVector)
      )
    )
  }

  val section = tag("section")
  val aside   = tag("aside")
  val nav     = tag("nav")
  val article = tag("article")

  def about =
    p(whoosh(_.Logo.Subtitle), site.tagline)

  def staticNav =
    section(
      ul(
        whoosh(_.Aside.StaticLinks.Container),
        site.links.map { case (title, url) =>
          li(
            a(
              whoosh(_.Aside.StaticLinks.Link),
              href := url,
              title
            )
          )
        }
      )
    )
}
