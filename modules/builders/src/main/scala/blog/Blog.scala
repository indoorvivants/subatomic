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
import subatomic.builders._
import subatomic.buildrs.blog.themes.default

import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension
import com.vladsch.flexmark.util.misc.Extension

case class Blog(
    override val contentRoot: os.Path,
    override val assetsRoot: Option[os.Path] = None,
    override val base: SitePath = SiteRoot,
    name: String,
    tagline: Option[String] = None,
    copyright: Option[String] = None,
    githubUrl: Option[String] = None,
    customTemplate: Option[Template] = None,
    links: Vector[(String, String)] = Vector.empty,
    override val highlighting: SyntaxHighlighting =
      SyntaxHighlighting.PrismJS.default,
    override val assetsFilter: os.Path => Boolean = _ => true,
    override val trackers: Seq[Tracker] = Seq.empty,
    search: Boolean = true,
    additionalMarkdownExtensions: Vector[Extension] = Vector.empty
) extends subatomic.builders.Builder {
  def markdownExtensions =
    RelativizeLinksExtension(base.toRelPath) +:
      (Blog.Defaults.markdownExtensions ++ additionalMarkdownExtensions)
}

sealed trait Doc {
  val title: String
}

case class Post(
    title: String,
    path: os.Path,
    date: LocalDate,
    description: Option[String],
    tags: List[String],
    mdocConfig: Option[MdocConfiguration],
    archived: Boolean,
    headings: Vector[Heading]
) extends Doc {
  def scalajsEnabled: Boolean = mdocConfig.exists(_.scalajsConfig.nonEmpty)
}

object Post {
  implicit val ordering: Ordering[Post] = Ordering.by(_.date.toEpochDay())
}

case class Heading(level: Int, title: String, url: String)

case class TagPage(
    tag: String,
    posts: List[Post]
) extends Doc {
  override val title = s"Posts tagged with $tag"
}

object Blog {

  trait App {
    def extra(site: Site[Doc]) = site

    def config: Blog

    def main(args: Array[String]): Unit = {

      import cli._

      command.parse(args.toList) match {
        case Left(value) =>
          println(value)
          sys.exit(-1)
        case Right(buildConfig: BuildConfig) =>
          createSite(
            config,
            buildConfig,
            extra _
          )
        case Right(search: SearchConfig) =>
          testSearch(config, search)
      }
    }
  }

  object Defaults {
    val markdownExtensions: Vector[Extension] = Vector(
      YamlFrontMatterExtension.create(),
      AnchorLinkExtension.create(),
      AutolinkExtension.create(),
      TargetBlankExtension.create()
    )
  }

  private[subatomic] def createRss(
      config: Blog,
      linker: Linker,
      domain: String,
      content: Vector[Doc]
  ) = {
    val posts = content.collect { case p: Post => p }

    import util.rss._

    val items = posts.map { p =>
      Item
        .create(Item.Title(p.title), Item.Link(domain + "/" + linker.find(p)))
        .copy(description = p.description.map(Item.Description.apply))
    }

    val channel = Channel
      .create(
        title = Channel.Title(config.name),
        link = Channel.Link(domain),
        description = Channel.Description(config.tagline.getOrElse(""))
      )
      .addItems(items: _*)

    RSS(channel = channel).render
  }

  def createNavigation(
      linker: Linker,
      content: Vector[Doc]
  ): Doc => Vector[NavLink] = {

    val all = content
      .collect {
        case doc: Post if !doc.archived =>
          doc -> NavLink(linker.find(doc), doc.title, selected = false)
      }
      .sortBy(_._1)
      .reverse

    { piece =>
      all.map {
        case (`piece`, link) => link.copy(selected = true)
        case (_, link)       => link
      }
    }
  }

  def markdownParser(siteConfig: Blog) =
    Markdown(
      siteConfig.markdownExtensions: _*
    )

  def discoverContent(siteConfig: Blog): Vector[(SitePath, Doc)] = {
    val posts = Discover
      .someMarkdown(siteConfig.contentRoot) {
        case MarkdownDocument(path, filename, attributes) =>
          // TODO: handle the error here correctly
          val date = LocalDate.parse(attributes.requiredOne("date"))
          val tags =
            attributes.optionalOne("tags").toList.flatMap(_.split(",").toList)
          val title       = attributes.requiredOne("title")
          val description = attributes.optionalOne("description")
          // TODO: handle error here correctly
          val archived =
            attributes.optionalOne("archived").map(_.toBoolean).getOrElse(false)

          val sitePath = SiteRoot / (date.format(
            DateTimeFormatter.ISO_LOCAL_DATE
          ) + "-" + filename + ".html")

          val mdocConfig = MdocConfiguration.fromAttrs(attributes)

          val headings =
            markdownParser(siteConfig)
              .extractMarkdownSections("", "", path)
              .collect { case Markdown.Section(title, level, Some(url), _) =>
                Heading(level, title, url)
              }

          val post = Post(
            title,
            path,
            date,
            description,
            tags,
            mdocConfig,
            archived,
            headings
          ): Doc

          sitePath -> post
      }
      .toVector

    val tagPages = posts
      .map(_._2)
      .collect { case p: Post =>
        p
      }
      .flatMap(post => post.tags.map(tag => tag -> post))
      .groupBy(_._1)
      .toVector
      .map { case (tag, posts) =>
        SiteRoot / "tags" / s"$tag.html" ->
          TagPage(tag, posts.map(_._2).sorted.reverse.toList)
      }

    posts ++ tagPages
  }

  def createSite(
      siteConfig: Blog,
      buildConfig: cli.BuildConfig,
      extra: Site[Doc] => Site[Doc]
  ): Unit = {
    val content = discoverContent(siteConfig)

    val linker = new Linker(content, siteConfig.base)

    val navigation = createNavigation(linker, content.map(_._2))

    val template = siteConfig.customTemplate.getOrElse(
      Default(
        siteConfig,
        linker,
        content.map(_._2).collect { case t: TagPage =>
          t
        }
      )
    )

    val markdown = markdownParser(siteConfig)

    val mdocProcessor =
      if (!buildConfig.disableMdoc)
        MdocProcessor.create[Post]() {
          case Post(_, path, _, _, _, Some(config), _, _)
              if config.scalajsConfig.nonEmpty =>
            MdocFile(path, config)
        }
      else {
        Processor.simple[Post, MdocResult[Post]](doc =>
          MdocResult(doc, doc.path)
        )
      }

    val mdocJSProcessor: Processor[Post, (Post, Option[MdocJSResult[Post]])] =
      if (!buildConfig.disableMdoc)
        MdocJSProcessor
          .create[Post]() {
            case Post(_, path, _, _, _, Some(config), _, _)
                if config.scalajsConfig.nonEmpty =>
              MdocFile(path, config)
          }
          .map { result =>
            result.original -> Option(result)
          }
      else {
        Processor.simple(doc => doc -> None)
      }

    def renderPost(
        title: String,
        tags: Seq[String],
        file: os.Path,
        links: Vector[NavLink],
        headings: Vector[Heading]
    ) = {
      val renderedMarkdown = markdown.renderToString(file)
      val renderedHtml =
        template.post(
          links,
          headings,
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
          navigation(mdocResult.original),
          mdocResult.original.headings
        )
      }
    val mdocJSPageRenderer
        : Processor[Post, Map[SitePath => SitePath, SiteAsset]] =
      mdocJSProcessor
        .map { res =>
          res match {

            case (doc, Some(mdocResult)) =>
              Map(
                (identity[SitePath] _) ->
                  renderPost(
                    doc.title,
                    doc.tags,
                    mdocResult.markdownFile,
                    navigation(doc),
                    doc.headings
                  ),
                ((sp: SitePath) => sp.up / mdocResult.jsSnippetsFile.last) ->
                  CopyOf(mdocResult.jsSnippetsFile),
                (
                    (sp: SitePath) =>
                      sp.up / mdocResult.jsInitialisationFile.last
                ) ->
                  CopyOf(
                    mdocResult.jsInitialisationFile
                  )
              )

            case (doc, None) =>
              Map(
                (identity[SitePath] _) -> renderPost(
                  doc.title,
                  doc.tags,
                  doc.path,
                  navigation(doc),
                  doc.headings
                )
              )
          }
        }

    val baseSite = Site
      .init(content)
      .populate { case (site, content) =>
        content match {
          case (sitePath, doc: Post)
              if doc.mdocConfig.nonEmpty && !doc.scalajsEnabled =>
            site.addProcessed(sitePath, mdocPageRenderer, doc)
          case (sitePath, doc: Post) if doc.mdocConfig.nonEmpty =>
            site.addProcessed(
              mdocJSPageRenderer.map { mk =>
                mk.map { case (k, v) => k.apply(sitePath) -> v }
              },
              doc
            )
          case (sitePath, doc: Post) =>
            site.add(
              sitePath,
              renderPost(
                doc.title,
                doc.tags,
                doc.path,
                navigation(doc),
                doc.headings
              )
            )
          case (sitePath, doc: TagPage) =>
            site.addPage(
              sitePath,
              template.tagPage(navigation(doc), doc.tag, doc.posts).render
            )
        }
      }

    def addIndexPage(site: Site[Doc]): Site[Doc] = {
      val blogPosts = content.map(_._2).collect {
        case p: Post if !p.archived => p
      }

      site.addPage(
        SiteRoot / "index.html",
        template.indexPage(blogPosts).render
      )
    }

    def addArchivePage(site: Site[Doc]): Site[Doc] = {
      val blogPosts = content.map(_._2).collect {
        case p: Post if p.archived => p
      }

      site.addPage(
        SiteRoot / "archive.html",
        template.archivePage(blogPosts).render
      )
    }

    val addTemplateCSS: Site[Doc] => Site[Doc] = site =>
      site.add(SiteRoot / "assets" / "template.css", Page(default.asString))

    val addRSSPage: Site[Doc] => Site[Doc] = site =>
      site.add(
        SiteRoot / "atom.xml",
        Page(createRss(siteConfig, linker, "hello", content.map(_._2)))
      )

    val builderSteps = new BuilderSteps(markdown)

    val steps = List[Site[Doc] => Site[Doc]](
      builderSteps.addSearchIndex[Doc](
        linker,
        { case p: Post =>
          BuilderSteps.SearchableDocument(p.title, p.path)
        },
        content
      ),
      builderSteps
        .addAllAssets[Doc](siteConfig.assetsRoot, siteConfig.assetsFilter),
      addTemplateCSS,
      addRSSPage,
      extra
    )

    val process = steps.foldLeft(identity[Site[Doc]] _) { case (step, next) =>
      step andThen next
    }

    val extraSteps: Site[Doc] => Site[Doc] = site =>
      process(addIndexPage(addArchivePage(site)))

    extraSteps(baseSite).buildAt(buildConfig.destination, buildConfig.overwrite)
  }

  def testSearch(siteConfig: Blog, searchConfig: cli.SearchConfig) = {
    val content = discoverContent(siteConfig)

    val linker   = new Linker(content, siteConfig.base)
    val markdown = markdownParser(siteConfig)

    val builderSteps = new BuilderSteps(markdown)

    val idx =
      builderSteps
        .buildSearchIndex[Doc](
          linker,
          { case p: Post => BuilderSteps.SearchableDocument(p.title, p.path) }
        )(content)

    searchConfig.mode match {
      case cli.Interactive =>
        subatomic.search.Search.cli(idx, searchConfig.debug)
      case cli.Query(q) =>
        subatomic.search.Search.query(idx, q, searchConfig.debug)
    }
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
    tagPages: Seq[TagPage]
) extends Template
