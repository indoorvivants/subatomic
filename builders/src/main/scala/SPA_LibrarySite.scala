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
package builders.spa.librarysite

import subatomic.Discover.MarkdownDocument
import subatomic.builders._
import subatomic.buildrs.librarysite.themes.default

import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension

case class SpaLibrarySite(
    contentRoot: os.Path,
    override val assetsRoot: Option[os.Path] = None,
    override val base: SitePath = SiteRoot,
    override val assetsFilter: os.Path => Boolean = _ => true,
    name: String,
    copyright: Option[String] = None,
    githubUrl: Option[String] = None,
    tagline: Option[String] = None,
    links: Vector[(String, String)] = Vector.empty,
    override val highlightJS: HighlightJS = HighlightJS.default,
    search: Boolean = true
) extends subatomic.builders.Builder

object SpaLibrarySite {

  case class Doc(
      title: String,
      path: os.Path,
      inNavBar: Boolean,
      index: Boolean = false,
      mdocConfig: Option[MdocConfiguration] = None,
      depth: Seq[String]
  ) {
    def scalajsEnabled = mdocConfig.exists(_.scalajsConfig.nonEmpty)
  }

  trait App {
    def extra(site: Site[SpaLibrarySite.Doc]) = site

    def config: SpaLibrarySite

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

  def discoverContent(siteConfig: SpaLibrarySite) = {
    Discover
      .someMarkdown(siteConfig.contentRoot) {
        case MarkdownDocument(path, filename, attributes) =>
          val id = attributes.optionalOne("id").getOrElse(filename)

          val inNavBar = attributes
            .optionalOne("topnav")
            .map(_.toBoolean)
            .getOrElse(false)

          val title = attributes.requiredOne("title")

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

          val document = Doc(
            title,
            path,
            inNavBar,
            index = isIndex,
            mdocConfig = mdocConfig,
            depth = relp.segments
          )

          sitePath -> document
      }
      .toVector
      .sortBy(_._1 == SiteRoot / "index.html")
      .reverse
  }

  def markdownParser(siteConfig: SpaLibrarySite) = {
    Markdown(
      RelativizeLinksExtension(siteConfig.base.toRelPath),
      YamlFrontMatterExtension.create(),
      AnchorLinkExtension.create()
    )
  }

  def createSite(
      siteConfig: SpaLibrarySite,
      buildConfig: cli.BuildConfig,
      extra: Site[SpaLibrarySite.Doc] => Site[SpaLibrarySite.Doc]
  ) = {

    val content = discoverContent(siteConfig)

    val markdown = markdownParser(siteConfig)

    val linker = new Linker(content, siteConfig.base)

    val mdocProcessor =
      if (!buildConfig.disableMdoc)
        MdocProcessor.create[Doc]() {
          case Doc(_, path, _, _, Some(config), _) if config.scalajsConfig.isEmpty => MdocFile(path, config)
        }
      else {
        Processor.simple[Doc, MdocResult[Doc]](doc => MdocResult(doc, doc.path))
      }

    val mdocJSProcessor: Processor[Doc, (Doc, Option[MdocJSResult[Doc]])] =
      if (!buildConfig.disableMdoc)
        MdocJSProcessor
          .create[Doc]() {
            case Doc(_, path, _, _, Some(config), _) if config.scalajsConfig.nonEmpty => MdocFile(path, config)
          }
          .map { result =>
            result.original -> Option(result)
          }
      else {
        Processor.simple(doc => doc -> None)
      }

    def renderMarkdownPage(
        file: os.Path
    ) = {
      val renderedMarkdown = markdown.renderToString(file)
      val renderedHtml     = RawHTML(renderedMarkdown)

      Page(renderedHtml.render)
    }

    val mdocPageRenderer: Processor[Doc, SiteAsset] = mdocProcessor
      .map { mdocResult =>
        renderMarkdownPage(
          mdocResult.resultFile
        )
      }

    val mdocJSPageRenderer: Processor[Doc, Map[SitePath => SitePath, SiteAsset]] = mdocJSProcessor
      .map { res =>
        res match {

          case (_, Some(mdocResult)) =>
            Map(
              (identity[SitePath] _) ->
                renderMarkdownPage(mdocResult.markdownFile),
              ((sp: SitePath) => sp.up / mdocResult.jsSnippetsFile.last) ->
                CopyOf(mdocResult.jsSnippetsFile),
              ((sp: SitePath) => sp.up / mdocResult.jsInitialisationFile.last) ->
                CopyOf(
                  mdocResult.jsInitialisationFile
                )
            )

          case (doc, None) =>
            Map(
              (identity[SitePath] _) -> renderMarkdownPage(doc.path)
            )
        }
      }

    val pagesRoot = SiteRoot / "_pages"

    val baseSite = Site
      .init(content)
      .populate {
        case (site, content) =>
          content match {
            case (sitePath, doc: Doc) if doc.mdocConfig.nonEmpty && !doc.scalajsEnabled =>
              site.addProcessed(pagesRoot / sitePath, mdocPageRenderer, doc)

            case (sitePath, doc: Doc) if doc.scalajsEnabled =>
              site.addProcessed(
                mdocJSPageRenderer.map { mk =>
                  mk.map { case (k, v) => k.apply(sitePath) -> v }
                },
                doc
              )
            case (sitePath, doc: Doc) =>
              site.add(SiteRoot / sitePath, renderMarkdownPage(doc.path))
          }
      }

    val addTemplateCSS: Site[Doc] => Site[Doc] = site =>
      site.add(SiteRoot / "assets" / "template.css", Page(default.asString))

    val addJsonConfig: Site[Doc] => Site[Doc] = { site =>
      import ujson._

      def removeHtml(p: SitePath) = 
        if(p.segments.lastOption.contains("index.html"))
          p.up / "index"
        else p

      val siteConfigJson = Obj(
        "pages" -> Arr.from(
          content.map {
            case (path, doc) =>
              Arr(Str(doc.title), Arr.from(removeHtml(path).segments))
          }
        )
      ).render()

      site
        .addPage(SiteRoot / "site.json", siteConfigJson)
        .addCopyOf(SiteRoot / "spa.js", os.temp(subatomic.spa.SpaPack.fullJS))
    }

    val addIndexPage: Site[Doc] => Site[Doc] = {site =>
      site.addPage(SiteRoot / "index.html", IndexHTML.apply(siteConfig.base).render)
    }

    val builderSteps = new BuilderSteps(markdown)

    val steps = List[Site[Doc] => Site[Doc]](
      builderSteps
        .addSearchIndex[Doc](linker, { case doc => BuilderSteps.SearchableDocument(doc.title, doc.path) }, content),
      builderSteps.addAllAssets[Doc](siteConfig.assetsRoot, siteConfig.assetsFilter),
      addTemplateCSS,
      addJsonConfig,
      addIndexPage,
      extra
    )

    val process = steps.foldLeft(identity[Site[Doc]] _) { case (step, next) => step andThen next }

    process(baseSite).buildAt(buildConfig.destination, buildConfig.overwrite)
  }

  def testSearch(siteConfig: SpaLibrarySite, searchConfig: cli.SearchConfig) = {
    val content = discoverContent(siteConfig)

    val linker   = new Linker(content, siteConfig.base)
    val markdown = markdownParser(siteConfig)

    val builderSteps = new BuilderSteps(markdown)

    val idx =
      builderSteps
        .buildSearchIndex[Doc](linker, { case doc => BuilderSteps.SearchableDocument(doc.title, doc.path) })(content)

    searchConfig.mode match {
      case cli.Interactive => subatomic.search.Search.cli(idx, searchConfig.debug)
      case cli.Query(q)    => subatomic.search.Search.query(idx, q, searchConfig.debug)
    }
  }
}

object RawHTML {

  import scalatags.Text.all._
  def apply(rawHtml: String) = raw(rawHtml)
}

object IndexHTML {
  import scalatags.Text.all._
  def apply(shift: SitePath) =
    html(
      head(
        meta(charset := "UTF-8")
      ),
      body(
        div(id := "app"),
        script(`type` := "text/javascript", src := (shift.segments ++ Seq("spa.js")).mkString("/", "/", ""))
      )
    )

}
