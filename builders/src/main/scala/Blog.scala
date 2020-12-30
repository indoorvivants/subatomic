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
package builders.blog

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import subatomic.Discover.MarkdownDocument
import subatomic.builders.Highlight

import cats.implicits._
import com.monovore.decline._
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension

case class Blog(
    contentRoot: os.Path,
    assetsRoot: Option[os.Path] = None,
    base: SitePath = SiteRoot,
    name: String,
    copyright: Option[String] = None,
    githubUrl: Option[String] = None,
    tagline: Option[String] = None,
    customTemplate: Option[Template] = None,
    links: Vector[(String, String)] = Vector.empty,
    highlightJS: Highlight = Highlight.default
)

sealed trait Doc {
  val title: String
}

case class Post(
    title: String,
    path: os.Path,
    date: LocalDate,
    description: Option[String],
    tags: List[String],
    mdocConfig: Option[MdocConfig],
    archived: Boolean
) extends Doc

case class TagPage(
    tag: String,
    posts: List[Post]
) extends Doc {
  override val title = s"Posts tagged with $tag"
}

case class MdocConfig(
    dependencies: List[String]
)

object MdocConfig {
  def from(attrs: Discover.YamlAttributes): Option[MdocConfig] = {
    val enabled      = attrs.optionalOne("scala-mdoc").getOrElse("false").toBoolean
    val dependencies = attrs.optionalOne("scala-mdoc-dependencies").map(_.split(",").toList).getOrElse(Nil)

    if (enabled) Some(MdocConfig(dependencies)) else None
  }
}

object Blog {

  object cli {
    case class Config(
        destination: os.Path,
        disableMdoc: Boolean,
        overwrite: Boolean
    )
    implicit val pathArgument: Argument[os.Path] =
      Argument[String].map(s => os.Path.apply(s))

    private val disableMdoc = Opts
      .flag(
        "disable-mdoc",
        "Don't call mdoc. This greatly speeds up things and is useful for iterating on the design"
      )
      .orFalse

    private val destination = Opts
      .option[os.Path](
        "destination",
        help = "Absolute path where the static site will be generated"
      )
      .withDefault(os.temp.dir())

    private val overwrite = Opts.flag("overwrite", "Overwrite files if present at destination").orFalse

    val command = Command("build site", "builds the site")(
      (destination, disableMdoc, overwrite).mapN(Config)
    )
  }

  trait App {
    def extra(site: Site[Doc]) = site

    def config: Blog

    def main(args: Array[String]): Unit = {

      import cli._

      command.parse(args.toList) match {
        case Left(value) =>
          println(value)
          sys.exit(-1)
        case Right(buildConfig) =>
          createSite(
            config,
            buildConfig,
            extra _
          )
      }
    }
  }

  def createNavigation(linker: Linker, content: Vector[Doc]): Doc => Vector[NavLink] = {
    val all = content
      .collect {
        case doc: Post if !doc.archived => doc -> NavLink(linker.find(doc), doc.title, selected = false)
      }
      .sortBy(-_._1.date.toEpochDay())

    { piece =>
      all.map {
        case (`piece`, link) => link.copy(selected = true)
        case (_, link)       => link
      }
    }
  }

  def createSite(
      siteConfig: Blog,
      buildConfig: cli.Config,
      extra: Site[Doc] => Site[Doc]
  ) = {
    val posts = Discover
      .someMarkdown(siteConfig.contentRoot) {
        case MarkdownDocument(path, filename, attributes) =>
          val date        = LocalDate.parse(attributes.requiredOne("date"))
          val tags        = attributes.optionalOne("tags").toList.flatMap(_.split(",").toList)
          val title       = attributes.requiredOne("title")
          val description = attributes.optionalOne("description")
          val archived    = attributes.optionalOne("archived").map(_.toBoolean).getOrElse(false)

          val sitePath = SiteRoot / (date.format(DateTimeFormatter.ISO_LOCAL_DATE) + "-" + filename + ".html")

          val mdocConfig = MdocConfig.from(attributes)

          val post = Post(
            title,
            path,
            date,
            description,
            tags,
            mdocConfig,
            archived
          ): Doc

          sitePath -> post
      }
      .toVector

    val tagPages = posts
      .map(_._2)
      .collect {
        case p: Post => p
      }
      .flatMap(post => post.tags.map(tag => tag -> post))
      .groupBy(_._1)
      .toVector
      .map {
        case (tag, posts) =>
          SiteRoot / "tags" / s"$tag.html" -> TagPage(tag, posts.map(_._2).toList)
      }

    val content = posts ++ tagPages

    val markdown = Markdown(
      RelativizeLinksExtension(siteConfig.base.toRelPath),
      YamlFrontMatterExtension.create()
    )

    val linker = new Linker(content, siteConfig.base)

    val navigation = createNavigation(linker, content.map(_._2))

    val managedStyles = siteConfig.assetsRoot
      .map { path =>
        os.walk(path).filter(_.ext == "css").map(_.relativeTo(path)).map(rel => SiteRoot / "assets" / rel)
      }
      .getOrElse(Nil)
      .toList

    val managedScripts = siteConfig.assetsRoot
      .map { path =>
        os.walk(path).filter(_.ext == "js").map(_.relativeTo(path)).map(rel => SiteRoot / "assets" / rel)
      }
      .getOrElse(Nil)
      .toList

    val template = siteConfig.customTemplate.getOrElse(
      Default(siteConfig, linker, tagPages.map(_._2), managedScripts = managedScripts, managedStyles = managedStyles)
    )

    val mdocProcessor =
      if (!buildConfig.disableMdoc)
        MdocProcessor.create[Post]() {
          case Post(_, path, _, _, _, Some(config), _) => MdocFile(path, config.dependencies.toSet)
        }
      else {
        Processor.simple[Post, MdocResult[Post]](doc => MdocResult(doc, doc.path))
      }

    def renderPost(title: String, tags: Seq[String], file: os.Path, links: Vector[NavLink]) = {
      val renderedMarkdown = markdown.renderToString(file)
      val renderedHtml =
        template.post(
          links,
          title,
          tags,
          renderedMarkdown
        )

      Page(renderedHtml)
    }

    val mdocPageRenderer: Processor[Post, SiteAsset] = mdocProcessor
      .map { mdocResult =>
        renderPost(
          mdocResult.original.title,
          mdocResult.original.tags,
          mdocResult.resultFile,
          navigation(mdocResult.original)
        )
      }

    val baseSite = Site
      .init(content)
      .populate {
        case (site, content) =>
          content match {
            case (sitePath, doc: Post) if doc.mdocConfig.nonEmpty =>
              site.addProcessed(sitePath, mdocPageRenderer, doc)
            case (sitePath, doc: Post) =>
              site.add(sitePath, renderPost(doc.title, doc.tags, doc.path, navigation(doc)))
            case (sitePath, doc: TagPage) =>
              site.addPage(sitePath, template.tagPage(navigation(doc), doc.tag, doc.posts).render)
          }
      }

    def addIndexPage(site: Site[Doc]): Site[Doc] = {
      val blogPosts = content.map(_._2).collect {
        case p: Post if !p.archived => p
      }

      site.addPage(SiteRoot / "index.html", template.indexPage(blogPosts).render)
    }

    def addArchivePage(site: Site[Doc]): Site[Doc] = {
      val blogPosts = content.map(_._2).collect {
        case p: Post if p.archived => p
      }

      site.addPage(SiteRoot / "archive.html", template.archivePage(blogPosts).render)
    }

    def addAllAssets(site: Site[Doc]) = {
      siteConfig.assetsRoot match {
        case Some(path) => site.copyAll(path, SiteRoot / "assets")
        case None       => site
      }
    }

    val extraSteps: Site[Doc] => Site[Doc] = site => extra(addAllAssets(addIndexPage(addArchivePage(site))))

    extraSteps(baseSite).buildAt(buildConfig.destination, buildConfig.overwrite)
  }
}

case class NavLink(
    url: String,
    title: String,
    selected: Boolean
)

case class Default(
    site: Blog,
    linker: Linker,
    tagPages: Seq[TagPage],
    managedStyles: Seq[SitePath] = Nil,
    managedScripts: Seq[SitePath] = Nil
) extends Template

trait Template {

  def site: Blog
  def linker: Linker
  def managedScripts: Seq[SitePath]
  def managedStyles: Seq[SitePath]
  def tagPages: Seq[TagPage]

  import scalatags.Text.all._
  import scalatags.Text.TypedTag

  def highlightJsBlock = {
    val styles     = site.highlightJS.styles.map(s => link(rel := "stylesheet", href := s))
    val scripts    = site.highlightJS.scripts.map(s => script(src := s))
    val initScript = script(raw(site.highlightJS.initScript))

    (styles ++ scripts) ++ List(initScript)
  }

  def Nav(navigation: Vector[NavLink]) = {
    div(
      navigation.sortBy(_.title).map {
        case NavLink(_, title, selected) if selected =>
          p(strong(title))
        case NavLink(url, title, _) =>
          p(a(href := url, title))
      }
    )
  }

  def rawHtml(rawHtml: String) = div(raw(rawHtml))

  def managedStylesheetsBlock =
    managedStyles.map { sp =>
      link(
        rel := "stylesheet",
        href := linker.unsafe(_ => sp)
      )
    }

  def managedScriptsBlock =
    managedScripts.map { sp =>
      script(src := linker.unsafe(_ => sp))
    }

  def basePage(navigation: Option[Vector[NavLink]], content: TypedTag[_]) = {
    val pageTitle = navigation
      .flatMap(_.find(_.selected))
      .map(_.title)
      .map(": " + _)
      .getOrElse("")

    html(
      head(
        scalatags.Text.tags2.title(site.name + ":" + pageTitle),
        highlightJsBlock,
        managedStylesheetsBlock,
        managedScriptsBlock,
        meta(charset := "UTF-8"),
        meta(
          name := "viewport",
          attr("content") := "width=device-width, initial-scale=1"
        )
      ),
      body(
        div(
          cls := "wrapper",
          div(
            cls := "sidebar",
            h2(a(href := linker.root, site.name)),
            hr,
            about,
            staticNav,
            hr,
            h4("tags"),
            tagCloud,
            navigation match {
              case Some(value) => div(hr, h4("posts"), Nav(value))
              case None        => div()
            }
          ),
          tag("article")(cls := "content-wrapper", content)
        )
      )
    )
  }

  def page(navigation: Vector[NavLink], content: TypedTag[_]) =
    basePage(Some(navigation), content)

  def post(
      navigation: Vector[NavLink],
      title: String,
      tags: Seq[String],
      content: String
  ): String = post(navigation, title, tags, rawHtml(content))

  def post(
      navigation: Vector[NavLink],
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
    page(navigation, div(h2(title), p(tagline), hr, content)).render
  }

  def tagPage(
      navigation: Vector[NavLink],
      tag: String,
      blogs: Seq[Post]
  ) = {
    page(
      navigation,
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
    div(
      tagPages.toList.map { tagPage =>
        span(a(href := linker.find(tagPage), small(tagPage.tag)), " ")
      }
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
      div(
        h3(title),
        div(cls := "card-columns", blogs.sortBy(-_.date.toEpochDay()).map(blogCard).toVector)
      )
    )
  }

  def indexPage(
      blogs: Seq[Post]
  ) = {
    basePage(
      None,
      div(
        h3("Posts"),
        div(cls := "card-columns", blogs.sortBy(-_.date.toEpochDay()).map(blogCard).toVector),
        a(href := linker.unsafe(_ / "archive.html"), "Archive")
      )
    )
  }

  def archivePage(
      blogs: Seq[Post]
  ) = {
    basePage(
      None,
      div(
        h3("Archive"),
        div(cls := "card-columns", blogs.sortBy(-_.date.toEpochDay()).map(blogCard).toVector)
      )
    )
  }

  def about =
    div(
      p(site.tagline)
    )

  def staticNav =
    ul(
      site.links.map {
        case (title, url) =>
          li(
            a(
              href := url,
              title
            )
          )
      }
    )
}
