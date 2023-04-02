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

package subatomic.builders
package librarysite

import java.nio.file.Paths

import subatomic.Discover.MarkdownDocument
import subatomic._
import subatomic.builders._

import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension

case class LibrarySite(
    contentRoot: os.Path,
    override val assetsRoot: Option[os.Path] = None,
    override val base: SitePath = SiteRoot,
    override val assetsFilter: os.Path => Boolean = _ => true,
    name: String,
    copyright: Option[String] = None,
    githubUrl: Option[String] = None,
    tagline: Option[String] = None,
    theme: Theme = default,
    links: Vector[(String, String)] = Vector.empty,
    override val highlighting: SyntaxHighlighting =
      SyntaxHighlighting.HighlightJS.default,
    override val trackers: Seq[Tracker] = Seq.empty,
    search: Boolean = true
) extends subatomic.builders.Builder

object LibrarySite {

  sealed trait Doc extends Product with Serializable {
    def id: SitePath
    def order: Int
    def title: String
  }
  object Doc {
    case class Text(
        title: String,
        path: os.Path,
        inNavBar: Boolean,
        index: Boolean = false,
        mdocConfig: Option[MdocConfiguration] = None,
        id: SitePath,
        order: Int
    ) extends Doc {
      def scalajsEnabled = mdocConfig.exists(_.scalajsConfig.nonEmpty)
    }

    case class Section(
        title: String,
        id: SitePath,
        order: Int
    )

  }

  trait App {
    def extra(site: Site[LibrarySite.Doc]) = site

    def config: LibrarySite

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

  case class NavTree(
      level: Vector[(Doc, NavTree, Boolean)],
      depth: Int
  ) {
    override def toString() = {
      val sb = new StringBuilder
      def go(l: NavTree, i: Int): Unit = {
        l.level.foreach { case (doc, nt, expanded) =>
          sb.append(s"${" " * i} - ${doc.title} (${expanded})\n")
          go(nt, i + 1)
        }
      }

      go(this, 0)

      sb.result()
    }
  }

  def discoverContent(siteConfig: LibrarySite) = {
    val sections = Vector.newBuilder[(SitePath, Doc)]
    val docs = Discover
      .someMarkdown[(SitePath, Doc)](siteConfig.contentRoot) {
        case MarkdownDocument(path, filename, attributes) =>
          val id = attributes.optionalOne("id").getOrElse(filename)

          val inNavBar = attributes
            .optionalOne("topnav")
            .map(_.toBoolean)
            .getOrElse(false)

          val title = attributes.requiredOne("title")
          val order = attributes.optionalOne("order").getOrElse("0").toInt

          val mdocConfig = MdocConfiguration.fromAttrs(attributes)

          val isIndex = filename == "index"

          val relp = (path / os.up).relativeTo(
            siteConfig.contentRoot
          )

          val sitePath =
            if (!isIndex)
              SiteRoot / relp / id / "index.html"
            else
              SiteRoot / relp / "index.html"

          val depth = sitePath.up

          val document = Doc.Text(
            title,
            path,
            inNavBar,
            index = isIndex,
            mdocConfig = mdocConfig,
            id = depth,
            order = order
          )

          sections += (depth -> document)

          sitePath -> document
      }
      .toVector
      .sortBy(_._1 == SiteRoot / "index.html")
      .reverse

    val res = sections.result().toVector
    val mp  = res.toMap

    def go(cur: SitePath, level: Int): NavTree = {
      val thisLevel = res
        .filter { case (sp, _) =>
          sp.upSafe.contains(cur)
        }
        .sortBy(_._2.order)
      val subtrees = thisLevel.map { case (sp, doc) =>
        val rec = go(sp, level + 1)
        (doc, rec, false)
      }
      NavTree(
        subtrees,
        level
      )
    }

    val navTree = go(SiteRoot, 0)

    def mark(tree: NavTree, target: Doc): (NavTree, Boolean) = {
      val nt = tree.level.map { case (doc, sub, _) =>
        val (marked, any) = mark(sub, target)
        (doc, marked, doc == target || any)
      }

      tree.copy(level = nt) -> nt.exists(_._3 == true)
    }

    docs -> { (d: Doc) =>
      val (marked, _) = mark(navTree, d)

      marked.copy(level =
        (
          mp(SiteRoot),
          NavTree(Vector.empty, 1),
          d == mp(SiteRoot)
        ) +: marked.level
      )

    }
  }

  def markdownParser(siteConfig: LibrarySite) = {
    Markdown(
      RelativizeLinksExtension(siteConfig.base.toRelPath),
      YamlFrontMatterExtension.create(),
      AnchorLinkExtension.create()
    )
  }

  def createSite(
      siteConfig: LibrarySite,
      buildConfig: cli.BuildConfig,
      extra: Site[LibrarySite.Doc] => Site[LibrarySite.Doc]
  ) = {
    val dir =
      Paths.get(dev.dirs.BaseDirectories.get().cacheDir).resolve("subatomic")

    val tailwind = TailwindCSS.bootstrap(TailwindCSS.Config.default, dir)
    val (content, navigation) = discoverContent(siteConfig)

    val markdown = markdownParser(siteConfig)

    val linker = new Linker(content, siteConfig.base)

    val template = new DefaultPage(siteConfig, linker, siteConfig.theme)

    val mdocProcessor =
      if (!buildConfig.disableMdoc)
        MdocProcessor.create[Doc.Text]() {
          case Doc.Text(_, path, _, _, Some(config), _, _)
              if config.scalajsConfig.isEmpty =>
            MdocFile(path, config)
        }
      else {
        Processor.simple[Doc.Text, MdocResult[Doc.Text]] { case doc: Doc.Text =>
          MdocResult(doc, doc.path)
        }
      }

    val mdocJSProcessor
        : Processor[Doc.Text, (Doc.Text, Option[MdocJSResult[Doc.Text]])] =
      if (!buildConfig.disableMdoc)
        MdocJSProcessor
          .create[Doc.Text]() {
            case Doc.Text(_, path, _, _, Some(config), _, _)
                if config.scalajsConfig.nonEmpty =>
              MdocFile(path, config)
          }
          .map { result =>
            result.original -> Option(result)
          }
      else {
        Processor.simple(doc => doc -> None)
      }

    def renderMarkdownPage(
        title: String,
        file: os.Path,
        links: NavTree
    ) = {
      val renderedMarkdown = markdown.renderToString(file)
      val toc              = TOC.build(markdown.extractMarkdownHeadings(file))
      val renderedHtml = template.doc(
        title,
        renderedMarkdown,
        if (toc.length > 1) Some(toc) else None,
        links
      )

      Page(renderedHtml)
    }

    val mdocPageRenderer: Processor[Doc.Text, SiteAsset] = mdocProcessor
      .map { mdocResult =>
        renderMarkdownPage(
          mdocResult.original.title,
          mdocResult.resultFile,
          navigation(mdocResult.original)
        )
      }

    val mdocJSPageRenderer
        : Processor[Doc.Text, Map[SitePath => SitePath, SiteAsset]] =
      mdocJSProcessor
        .map { res =>
          res match {

            case (doc: Doc.Text, Some(mdocResult)) =>
              Map(
                (identity[SitePath] _) ->
                  renderMarkdownPage(
                    doc.title,
                    mdocResult.markdownFile,
                    navigation(doc)
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

            case (doc: Doc.Text, None) =>
              Map(
                (identity[SitePath] _) -> renderMarkdownPage(
                  doc.title,
                  doc.path,
                  navigation(doc)
                )
              )
          }
        }

    val baseSite = Site
      .init(content)
      .populate { case (site, content) =>
        content match {
          case (sitePath, doc: Doc.Text)
              if doc.mdocConfig.nonEmpty && !doc.scalajsEnabled =>
            site.addProcessed(sitePath, mdocPageRenderer, doc)

          case (sitePath, doc: Doc.Text) if doc.scalajsEnabled =>
            site.addProcessed(
              mdocJSPageRenderer.map { mk =>
                mk.map { case (k, v) => k.apply(sitePath) -> v }
              },
              doc
            )
          case (sitePath, doc: Doc.Text) =>
            site.add(
              sitePath,
              renderMarkdownPage(doc.title, doc.path, navigation(doc))
            )
        }
      }

    val builderSteps = new BuilderSteps(markdown)

    val steps = List[Site[Doc] => Site[Doc]](
      builderSteps
        .addSearchIndex[Doc](
          linker,
          { case doc: Doc.Text =>
            BuilderSteps.SearchableDocument(doc.title, doc.path)
          },
          content
        ),
      builderSteps
        .addAllAssets[Doc](siteConfig.assetsRoot, siteConfig.assetsFilter),
      extra,
      builderSteps
        .tailwindStep(
          buildConfig.destination,
          tailwind,
          template.theme.Markdown,
          template.theme.Search
        )
    )

    val process = steps.foldLeft(identity[Site[Doc]] _) { case (step, next) =>
      step andThen next
    }

    process(baseSite).buildAt(buildConfig.destination, buildConfig.overwrite)
  }

  def testSearch(siteConfig: LibrarySite, searchConfig: cli.SearchConfig) = {
    val (content, _) = discoverContent(siteConfig)

    val linker   = new Linker(content, siteConfig.base)
    val markdown = markdownParser(siteConfig)

    val builderSteps = new BuilderSteps(markdown)

    val idx =
      builderSteps
        .buildSearchIndex[Doc](
          linker,
          { case doc: Doc.Text =>
            BuilderSteps.SearchableDocument(doc.title, doc.path)
          }
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

case class DefaultPage(
    site: LibrarySite,
    linker: Linker,
    theme: Theme
) extends HtmlPage
