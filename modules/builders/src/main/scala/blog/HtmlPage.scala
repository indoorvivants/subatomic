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

import io.lemonlabs.uri.Url

trait HtmlPage {

  def site: Blog
  def linker: Linker
  def tagPages: Seq[TagPage]

  import scalatags.Text.all._
  import scalatags.Text.tags2.time
  import scalatags.Text.TypedTag

  def Nav(navigation: Vector[NavLink]) = {
    ul(
      cls := "sb-aside-navigation-container",
      navigation.map {
        case NavLink(_, title, selected) if selected =>
          li(
            span(
              cls := "sb-aside-navigation-link-current",
              cls := "sb-aside-navigation-link",
              title
            )
          )
        case NavLink(url, title, _) =>
          li(
            a(
              cls  := "sb-aside-navigation-link",
              href := url,
              title
            )
          )
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
      content: TypedTag[_],
      openGraph: List[OpenGraphTags] = Nil
  ) = {
    val pageTitle = navigation
      .flatMap(_.find(_.selected)) match {
      case None        => site.name
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
          List(StylesheetPath(SiteRoot / "assets" / "styles.css"))
        ),
        BuilderTemplate.managedScriptsBlock(linker, site.managedScripts),
        searchScripts,
        meta(charset := "UTF-8"),
        meta(
          name            := "viewport",
          attr("content") := "width=device-width, initial-scale=1"
        ),
        openGraph.map(OpenGraphTags.renderAsHtml)
      ),
      body(
        cls := "sb-body",
        div(
          cls := "sb-main-container",
          aside(
            cls := "sb-aside",
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
      cls := "sb-aside-section",
      h4(
        cls := "sb-aside-link-container",

        a(
          href := linker.unsafe(_ / "archive.html"),
          "Archive",
          cls := "sb-aside-link"
        )
      )
    )
  }

  private def navigationSection(navigation: Option[Vector[NavLink]]) =
    navigation match {
      case Some(value) =>
        section(
          cls := "sb-aside-section",
          h4(cls := "sb-aside-section-title", "posts"),
          nav(
            cls := "sb-aside-section-content",
            Nav(value)
          )
        )
      case None => span()
    }

  private def headingsSection(headings: Option[Vector[Heading]]) =
    headings match {
      case None        => span()
      case Some(value) =>
        section(
          cls := "sb-aside-section",
          h4(cls := "sb-aside-section-title", "contents"),
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
      cls := "sb-logo-container",
      a(cls := "sb-logo-title", href := linker.root, site.name),
      about
    )

  private def searchSection =
    section(
      cls := "sb-search-section",
      div(id := "sb-search-container")
    )

  def page(
      navigation: Vector[NavLink],
      headings: Option[Vector[Heading]],
      content: TypedTag[_],
      openGraph: List[OpenGraphTags]
  ) =
    basePage(Some(navigation), headings, content, openGraph)

  def postPage(
      navigation: Vector[NavLink],
      headings: Vector[Heading],
      title: String,
      description: Option[String],
      url: Url,
      tags: Seq[String],
      toc: Option[TOC],
      content: String,
      author: Option[Author]
  ): String = post(
    navigation,
    headings,
    title,
    description,
    url,
    tags,
    toc,
    author,
    article(
      cls := "sb-content-container",
      cls := "sb-post-content",
      { val x = toc.map(Html.renderTOC(_)); println(s"$title - $x"); x },
      rawHtml(content)
    )
  )

  def post(
      navigation: Vector[NavLink],
      headings: Vector[Heading],
      title: String,
      description: Option[String],
      url: Url,
      tags: Seq[String],
      toc: Option[TOC],
      author: Option[Author],
      content: TypedTag[_]
  ) = {
    val tagline = tags.toList.map { tag =>
      a(
        cls  := "sb-tag-link",
        href := linker.unsafe(_ / "tags" / s"$tag.html"),
        tag
      )
    }
    "<!DOCTYPE html>" + page(
      navigation,
      Some(headings),
      div(
        h2(cls := "sb-post-title", title),
        p(cls  := "sbt-post-tagline", tagline),
        author
          .map(author =>
            p(
              cls := "sb-post-author-container",
              "By ",
              a(
                cls  := "sb-post-author-link",
                href := linker.unsafe(_ / "author" / s"${author.id}.html"),
                author.name
              )
            )
          ),
        content
      ),
      List(
        OpenGraphTags.Type.Article,
        OpenGraphTags.Title(title),
        OpenGraphTags.Url(url.toAbsoluteUrl.toString())
      ) ++ description.map(OpenGraphTags.Description.apply).toList
    ).render
  }

  def authorPage(
      navigation: Vector[NavLink],
      author: Author,
      blogs: Seq[Post]
  ) = {
    page(
      navigation,
      None,
      div(
        h3(
          // whoosh(_.TagPage.Header),
          cls := "sb-page-header",
          "Posts by ",
          b(author.name)
        ),
        ul(
          // whoosh(_.AuthorPage.Links.Container),
          cls := "sb-authorpage-links-container",
          author.links.toList.sortBy(_._1).map { case (title, link) =>
            li(
              cls := "sb-authorpage-links-link-container",
              a(href := link, title, cls := "sb-authorpage-links-link")
            )
          }
        ),
        div(blogs.map(blogCard).toVector)
      ),
      List(
        OpenGraphTags.Type.Website,
        OpenGraphTags.Title(s"Posts by ${author.name}")
      )
    )
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
          cls := "sb-page-header",
          "Posts tagged with ",
          span(cls := "sb-tag", tag)
        ),
        div(blogs.map(blogCard).toVector)
      ),
      List(
        OpenGraphTags.Type.Website,
        OpenGraphTags.Title(s"Posts tagged `$tag`")
      )
    )
  }

  def dateFormat(dt: LocalDate) = dt.format(DateTimeFormatter.ISO_DATE)

  def tagCloud = {
    section(
      cls := "sb-aside-section",
      h4(cls := "sb-aside-section-title", "tags"),
      nav(
        cls := "sb-aside-section-content",
        cls := "sb-aside-tag-container",
        tagPages.sortBy(_.posts.length).reverse.take(30).toList.map { tagPage =>
          a(
            cls  := "sb-aside-tag-link",
            href := linker.find(tagPage),
            tagPage.tag + s" (${tagPage.posts.length})"
          )
        }
      )
    )
  }

  def blogCard(
      blogPost: Post
  ) = {
    section(
      cls := "sb-postcard-container",
      div(
        cls := "sb-postcard-body",
        div(
          cls := "sb-postcard-title-container",
          a(
            cls  := "sb-postcard-title",
            href := linker.find(blogPost),
            blogPost.title
          ),
          time(
            cls := "sb-postcard-date",
            dateFormat(blogPost.date)
          )
        ),
        p(cls := "sb-postcard-description", blogPost.description)
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
        h3(cls  := "sb-archive-link-container", "Archive"),
        div(cls := "sb-blog-cards", blogs.sorted.reverse.map(blogCard).toVector)
      )
    )
  }

  val section = tag("section")
  val aside   = tag("aside")
  val nav     = tag("nav")
  val article = tag("article")

  def about =
    p(cls := "sb-logo-subtitle", site.tagline)

  def staticNav =
    section(
      cls := "sb-aside-section",
      ul(
        cls := "sb-static-links-container",
        // whoosh(_.Aside.StaticLinks.Container),
        site.links.map { case (title, url) =>
          li(
            cls := "sb-static-links-link-container",
            a(
              cls  := "sb-static-links-link",
              href := url,
              title
            )
          )
        }
      )
    )
}
