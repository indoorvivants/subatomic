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

import java.nio.file.Paths
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import subatomic.Discover.MarkdownDocument
import subatomic.builders._
import subatomic.builders.blog.themes.Theme
import subatomic.builders.blog.themes.default

import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension
import com.vladsch.flexmark.util.misc.Extension
import io.lemonlabs.uri.Url

case class RSSConfig(
    description: String,
    title: String
)
case class Author(
    id: String,
    name: String,
    links: Map[String, String] = Map.empty
)

case class Blog(
    override val contentRoot: os.Path,
    override val assetsRoot: Option[os.Path] = None,
    override val base: SitePath = SiteRoot,
    name: String,
    tagline: Option[String] = None,
    copyright: Option[String] = None,
    githubUrl: Option[String] = None,
    theme: Theme = default,
    links: Vector[(String, String)] = Vector.empty,
    override val highlighting: SyntaxHighlighting =
      SyntaxHighlighting.HighlightJS.default,
    override val assetsFilter: os.Path => Boolean = _ => true,
    override val trackers: Seq[Tracker] = Seq.empty,
    search: Boolean = true,
    additionalMarkdownExtensions: Vector[Extension] = Vector.empty,
    rssConfig: Option[RSSConfig] = None,
    d2Config: D2.Config = D2.Config.default,
    tailwindConfig: TailwindCSS.Config = TailwindCSS.Config.default,
    publicUrl: Url,
    override val cache: Cache = Cache.NoCaching,
    authors: List[Author] = Nil
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
    headings: Vector[Heading],
    author: Option[String],
    hidden: Boolean
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

case class AuthorPage(
    author: Author,
    posts: List[Post]
) extends Doc {
  override val title = s"Posts by ${author.name}"
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
          new Builder(config).createSite(
            config,
            buildConfig,
            extra _
          )
        case Right(search: SearchConfig) =>
          new Builder(config).testSearch(config, search)
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

  class Builder(config: Blog) {

    def absoluteUrl(doc: Doc, linker: Linker, baseUrl: Url): Url = {
      val path = baseUrl.removeEmptyPathParts().path

      baseUrl.withPath(
        path.addParts(linker.findRelativePath(doc).segments)
      )
    }

    def createRss(
        baseUrl: Url,
        config: RSSConfig,
        linker: Linker,
        content: Vector[Doc]
    ) = {
      val rssPath = "rss.xml"
      val at      = SiteRoot / rssPath

      val posts = content
        .collect { case p: Post if !p.hidden => p }
        .sortBy(_.date)
        .reverse

      val feedUrl = baseUrl.removeEmptyPathParts().addPathPart(rssPath)

      import util.rss._

      val items = posts.map { post =>
        Item
          .create(
            Item.Title(post.title),
            Item.Link(absoluteUrl(post, linker, baseUrl).toString())
          )
          .copy(description = post.description.map(Item.Description.apply))
          .copy(publicationDate =
            Some(
              post.date
                .atStartOfDay()
                .atZone(ZoneId.of("UTC"))
                .toOffsetDateTime()
            )
          )
      }

      val channel = Channel
        .create(
          title = Channel.Title(config.title),
          link = Channel.Link(baseUrl.toString),
          description = Channel.Description(config.description)
        )
        .addItems(items: _*)

      at -> RSS(channel = channel, feedUrl = feedUrl).render
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

    def markdownParser(
        siteConfig: Blog,
        diagramResolver: Option[BuilderSteps.d2Resolver] = None
    ) =
      Markdown(
        parserExtensions =
          siteConfig.markdownExtensions.toList ++ diagramResolver
            .map(d2 =>
              D2Extension.create(d2.named(_), d2.immediate(_)).create()
            )
            .toList
      )

    def discoverContent(
        siteConfig: Blog,
        markdown: Markdown
    ): Vector[(SitePath, Doc)] = {
      val boolTrue  = Set("true", "yes", "y")
      val boolFalse = Set("false", "no", "n")
      val posts = Discover
        .someMarkdown(siteConfig.contentRoot, markdown) {
          case MarkdownDocument(path, filename, attributes) =>
            // TODO: handle the error here correctly
            val date = LocalDate.parse(attributes.requiredOne("date"))
            val tags =
              attributes.optionalOne("tags").toList.flatMap(_.split(",").toList)
            val title       = attributes.requiredOne("title")
            val description = attributes.optionalOne("description")
            val author      = attributes.optionalOne("author")
            val hidden =
              attributes.optionalOne("hidden").map(_.trim.toLowerCase()).map {
                value =>
                  if (boolFalse.contains(value)) false
                  else if (boolTrue.contains(value)) true
                  else
                    SubatomicError.raise(
                      s"Value [$value] cannot be interpreted as boolean; use one of ${boolFalse ++ boolTrue}"
                    )
              }
            // TODO: handle error here correctly
            val archived =
              attributes
                .optionalOne("archived")
                .map(_.toBoolean)
                .getOrElse(false)

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
              title = title,
              path = path,
              date = date,
              description = description,
              tags = tags,
              mdocConfig = mdocConfig,
              archived = archived,
              headings = headings,
              author = author,
              hidden = hidden.contains(true)
            ).asInstanceOf[Doc]

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

      val authorPages = posts
        .map(_._2)
        .collect { case p: Post =>
          p
        }
        .flatMap(post =>
          post.author.map(resolveAuthor).map(author => author -> post)
        )
        .groupBy(_._1)
        .toVector
        .map { case (author, posts) =>
          SiteRoot / "author" / s"${author.id}.html" ->
            AuthorPage(author, posts.map(_._2).sorted.reverse.toList)
        }

      posts ++ tagPages ++ authorPages
    }

    def resolveAuthor(id: String) = {
      val availableAuthors =
        config.authors
          .map(_.id)
          .mkString("[", "], [", "]")

      config.authors
        .find(_.id.equalsIgnoreCase(id.trim))
        .getOrElse(
          SubatomicError.raise(
            s"Could not find author with id [$id]. Choose one of $availableAuthors"
          )
        )
    }

    def createSite(
        siteConfig: Blog,
        buildConfig: cli.BuildConfig,
        extra: Site[Doc] => Site[Doc]
    ): Unit = {
      val tailwind = TailwindCSS.bootstrap(siteConfig.tailwindConfig)
      val d2 =
        D2.bootstrap(
          siteConfig.d2Config,
          Cache.verbose(Cache.labelled("d2", siteConfig.cache))
        )
      val d2Resolver        = BuilderSteps.d2Resolver(d2)
      val renderingMarkdown = markdownParser(siteConfig, Some(d2Resolver))
      val content =
        discoverContent(siteConfig, markdownParser(siteConfig, None))

      val linker = new Linker(content, siteConfig.base)

      val navigation = createNavigation(
        linker,
        content.map(_._2).collect {
          case p: Post if !p.hidden => p
          case t: TagPage           => t
          case a: AuthorPage        => a
        }
      )

      val template =
        DefaultHtmlPage(
          site = siteConfig,
          linker = linker,
          tagPages = content.map(_._2).collect { case t: TagPage =>
            t
          },
          theme = siteConfig.theme
        )

      val mdocProcessor =
        if (!buildConfig.disableMdoc)
          MdocProcessor.create[Post]() {
            case Post(_, path, _, _, _, Some(config), _, _, _, _)
                if config.scalajsConfig.isEmpty =>
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
              // TODO: this is becoming unusable
              case Post(_, path, _, _, _, Some(config), _, _, _, _)
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
          description: Option[String],
          absoluteUrl: Url,
          tags: Seq[String],
          file: os.Path,
          links: Vector[NavLink],
          headings: Vector[Heading],
          author: Option[Author]
      ) = {
        val document = renderingMarkdown.read(file)
        val headers  = renderingMarkdown.extractMarkdownHeadings(document)
        val toc      = TOC.build(headers)
        val renderedMarkdown = renderingMarkdown.renderToString(document)
        val renderedHtml =
          template.postPage(
            navigation = links,
            headings = headings,
            title = title,
            description = description,
            url = absoluteUrl,
            tags = tags,
            toc = if (toc.length > 1) Some(toc) else None,
            content = renderedMarkdown,
            author = author
          )

        Page(renderedHtml)
      }

      val mdocPageRenderer: Processor[Post, SiteAsset] = mdocProcessor
        .map { mdocResult =>
          renderPost(
            mdocResult.original.title,
            mdocResult.original.description,
            absoluteUrl(mdocResult.original, linker, siteConfig.publicUrl),
            mdocResult.original.tags,
            mdocResult.resultFile,
            navigation(mdocResult.original),
            mdocResult.original.headings,
            mdocResult.original.author.map(resolveAuthor)
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
                      title = doc.title,
                      description = doc.description,
                      absoluteUrl =
                        absoluteUrl(doc, linker, siteConfig.publicUrl),
                      tags = doc.tags,
                      file = mdocResult.markdownFile,
                      links = navigation(doc),
                      headings = doc.headings,
                      author = doc.author.map(resolveAuthor)
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
                    title = doc.title,
                    description = doc.description,
                    absoluteUrl =
                      absoluteUrl(doc, linker, siteConfig.publicUrl),
                    tags = doc.tags,
                    file = doc.path,
                    links = navigation(doc),
                    headings = doc.headings,
                    author = doc.author.map(resolveAuthor)
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
                  title = doc.title,
                  description = doc.description,
                  absoluteUrl = absoluteUrl(doc, linker, siteConfig.publicUrl),
                  tags = doc.tags,
                  file = doc.path,
                  links = navigation(doc),
                  headings = doc.headings,
                  author = doc.author.map(resolveAuthor)
                )
              )
            case (sitePath, doc: TagPage) =>
              site.addPage(
                sitePath,
                template.tagPage(navigation(doc), doc.tag, doc.posts).render
              )
            case (sitePath, doc: AuthorPage) =>
              site.addPage(
                sitePath,
                template
                  .authorPage(navigation(doc), doc.author, doc.posts)
                  .render
              )
          }
        }

      def addIndexPage(site: Site[Doc]): Site[Doc] = {
        val blogPosts = content.map(_._2).collect {
          case p: Post if !p.archived && !p.hidden => p
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

      val addRSSPage: Site[Doc] => Site[Doc] = site =>
        siteConfig.rssConfig match {
          case None => site
          case Some(rss) =>
            val (at, xmlfeed) =
              createRss(siteConfig.publicUrl, rss, linker, content.map(_._2))

            site.add(
              at,
              Page(xmlfeed)
            )
        }

      val builderSteps = new BuilderSteps(markdownParser(siteConfig))

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
        addRSSPage,
        extra,
        builderSteps.tailwindStep(
          buildConfig.destination,
          tailwind,
          template.theme.Markdown,
          template.theme.Search
        ),
        builderSteps.d2Step(d2, d2Resolver.collected())
      )

      val process = steps.foldLeft(identity[Site[Doc]] _) { case (step, next) =>
        step andThen next
      }

      val extraSteps: Site[Doc] => Site[Doc] = site =>
        process(addIndexPage(addArchivePage(site)))

      extraSteps(baseSite).buildAt(
        buildConfig.destination,
        buildConfig.overwrite
      )
    }

    def testSearch(siteConfig: Blog, searchConfig: cli.SearchConfig) = {
      val markdown = markdownParser(siteConfig)
      val content  = discoverContent(siteConfig, markdown)

      val linker = new Linker(content, siteConfig.base)

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
}

case class NavLink(
    url: String,
    title: String,
    selected: Boolean
)

case class DefaultHtmlPage(
    site: Blog,
    linker: Linker,
    tagPages: Seq[TagPage],
    theme: Theme
) extends HtmlPage
