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
package builders.librarysite

import subatomic.Discover.MarkdownDocument
import subatomic.builders._
import subatomic.builders.librarysite.LibrarySite.Navigation

import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension

object Styles {
  import scalacss.DevDefaults._

  object default extends StyleSheet.Standalone {
    import dsl._

    "body" - (
      fontSize(19.px),
      color(black),
      backgroundColor(Color("#463F3A")),
      fontFamily.attr := "-apple-system,BlinkMacSystemFont,Segoe UI,Helvetica,Arial,sans-serif,Apple Color Emoji,Segoe UI Emoji,Segoe UI Symbol"
    )

    "div.container" - (
      backgroundColor(rgb(254, 255, 248)),
      borderRadius(5.px),
      padding(30.px),
      width(80.pc),
      margin(30.px),
      media.screen.minWidth(1600.px) - (
        width(50.%%),
        marginLeft(25.%%)
      )
    )

    "footer" - (
      width(80.%%),
      textAlign.center,
      marginLeft(30.px),
      padding(10.px),
      fontWeight.bold,
      color(c"#f4f3ee")
    )

    def extra =
      styleS(
        color.rgb(29, 49, 49),
        textDecorationLine.none.important,
        outline.none
      )

    "a" - (
      color.rgb(2, 47, 47),
      &.visited - extra,
      &.hover - extra,
      &.active - extra,
      &.focus - extra,
    )

    "header h1" - (
      margin(0.px)
    )

    "img.gh-logo" - (
      width(30.px),
      marginLeft(20.px)
    )

    "div.log img" - (
      width(75.px)
    )

    "header.main-header" - (
      display.flex,
      width(100.%%),
      flexDirection.row,
      flexWrap.nowrap,
      marginBottom(10.px)
    )

    "a.nav-btn" - (
      padding(10.px),
      marginRight(5.px),
      borderRadius(3.px),
      display.inlineBlock,
      textDecorationLine.underline,
      fontSize(20.px)
    )

    "a.nav-selected" - (
      borderLeft(0.px),
      backgroundColor.darkslategray,
      color.whitesmoke
    )

    "a.subnav-btn" - (
      padding(5.px),
      marginRight(5.px),
      borderRadius(3.px),
      display.inlineBlock,
      textDecorationLine.underline,
      fontSize(17.px)
    )

    "a.subnav-selected" - (
      borderLeft(0.px),
      backgroundColor.darkslategrey,
      color.whitesmoke
    )

    "div.site-title" - (
      alignSelf.flexStart,
      flexGrow(2)
    )

    "div.site-links" - (
      alignSelf.flexEnd
    )

    "div.terminal" - (
      overflow.scroll,
      color.white,
      backgroundColor.black
    )

    "div.terminal pre" - (
      backgroundColor.black,
      color.white,
      padding(20.px)
    )
  }
  def defaultCSS = default.renderA[String]
}

case class LibrarySite(
    contentRoot: os.Path,
    override val assetsRoot: Option[os.Path] = None,
    override val base: SitePath = SiteRoot,
    override val assetsFilter: os.Path => Boolean = _ => true,
    name: String,
    copyright: Option[String] = None,
    githubUrl: Option[String] = None,
    tagline: Option[String] = None,
    customTemplate: (LibrarySite, Linker) => Template = (s, l) => new Default(s, l),
    links: Vector[(String, String)] = Vector.empty,
    override val highlightJS: HighlightJS = HighlightJS.default,
    search: Boolean = true
) extends subatomic.builders.Builder

object LibrarySite {

  case class Doc(
      title: String,
      path: os.Path,
      inNavBar: Boolean,
      index: Boolean = false,
      mdocConfig: Option[MdocConfig] = None,
      depth: Seq[String]
  )

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

  case class Navigation(
      topLevel: Vector[NavLink],
      sameLevel: Option[Vector[NavLink]]
  )

  def createNavigation(
      linker: Linker,
      content: Vector[Doc]
  ): Doc => Navigation = {

    { piece =>
      @inline def mark(docs: Vector[Doc]) =
        docs.map {
          case `piece` => NavLink(linker.find(piece), piece.title, selected = true)
          case other   => NavLink(linker.find(other), other.title, selected = false)
        }

      val topLevel  = content.filter(doc => doc.depth.isEmpty || doc.inNavBar)
      val sameLevel = if (piece.depth.isEmpty) None else Some(content.filter(_.depth == piece.depth))

      Navigation(mark(topLevel), sameLevel.filter(_.size > 1).map(_.filter(!_.inNavBar)).map(mark))
    }

  }

  def discoverContent(siteConfig: LibrarySite) = {
    Discover
      .someMarkdown(siteConfig.contentRoot) {
        case MarkdownDocument(path, filename, attributes) =>
          val id = attributes.optionalOne("id").getOrElse(filename)

          val inNavBar = attributes
            .optionalOne("topnav")
            .map(_.toBoolean)
            .getOrElse(false)
          val title = attributes.requiredOne("title")

          val mdocConfig = MdocConfig.from(attributes)

          val isIndex = filename == "index"

          val relp = (path / os.up).relativeTo(
            siteConfig.contentRoot
          )

          val sitePath =
            if (!isIndex)
              SiteRoot / relp / id / "index.html"
            else
              SiteRoot / relp / "index.html"

          val document = Doc(title, path, inNavBar, index = isIndex, mdocConfig = mdocConfig, depth = relp.segments)

          sitePath -> document
      }
      .toVector
      .sortBy(_._1 == SiteRoot / "index.html")
      .reverse
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

    val content = discoverContent(siteConfig)

    val markdown = markdownParser(siteConfig)

    val linker = new Linker(content, siteConfig.base)

    val navigation = createNavigation(linker, content.map(_._2))

    val template = siteConfig.customTemplate(siteConfig, linker)

    val mdocProcessor =
      if (!buildConfig.disableMdoc)
        MdocProcessor.create[Doc]() {
          case Doc(_, path, _, _, Some(config), _) => MdocFile(path, config.dependencies.toSet)
        }
      else {
        Processor.simple[Doc, MdocResult[Doc]](doc => MdocResult(doc, doc.path))
      }

    def renderMarkdownPage(
        title: String,
        file: os.Path,
        links: Navigation
    ) = {
      val renderedMarkdown = markdown.renderToString(file)
      val renderedHtml     = template.doc(title, renderedMarkdown, links)

      Page(renderedHtml)
    }

    val mdocPageRenderer: Processor[Doc, SiteAsset] = mdocProcessor
      .map { mdocResult =>
        renderMarkdownPage(
          mdocResult.original.title,
          mdocResult.resultFile,
          navigation(mdocResult.original)
        )
      }

    val baseSite = Site
      .init(content)
      .populate {
        case (site, content) =>
          content match {
            case (sitePath, doc: Doc) if doc.mdocConfig.nonEmpty =>
              site.addProcessed(sitePath, mdocPageRenderer, doc)
            case (sitePath, doc: Doc) =>
              site.add(sitePath, renderMarkdownPage(doc.title, doc.path, navigation(doc)))
          }
      }

    val addTemplateCSS: Site[Doc] => Site[Doc] = site =>
      site.add(SiteRoot / "assets" / "template.css", Page(Styles.defaultCSS))

    val builderSteps = new BuilderSteps(markdown)

    val steps = List[Site[Doc] => Site[Doc]](
      builderSteps
        .addSearchIndex[Doc](linker, { case doc => BuilderSteps.SearchableDocument(doc.title, doc.path) }, content),
      builderSteps.addAllAssets[Doc](siteConfig.assetsRoot, siteConfig.assetsFilter),
      addTemplateCSS,
      extra
    )

    val process = steps.foldLeft(identity[Site[Doc]] _) { case (step, next) => step andThen next }

    process(baseSite).buildAt(buildConfig.destination, buildConfig.overwrite)
  }

  def testSearch(siteConfig: LibrarySite, searchConfig: cli.SearchConfig) = {
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

case class NavLink(
    url: String,
    title: String,
    selected: Boolean
)

case class Default(site: LibrarySite, linker: Linker) extends Template

trait Template {
  def site: LibrarySite
  def linker: Linker

  import scalatags.Text.all._
  import scalatags.Text.TypedTag

  def RawHTML(rawHtml: String) = div(raw(rawHtml))

  private def searchScripts = {
    val paths =
      if (site.search)
        List(ScriptPath(SiteRoot / "assets" / "search.js"), ScriptPath(SiteRoot / "assets" / "search-index.js"))
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
    val paths = List(StylesheetPath(SiteRoot / "assets" / "template.css"))

    BuilderTemplate.managedStylesBlock(linker, paths)
  }

  def doc(title: String, content: String, links: Navigation): String =
    doc(title, RawHTML(content), links)

  def doc(
      title: String,
      content: TypedTag[_],
      links: Navigation
  ): String = {
    html(
      head(
        scalatags.Text.tags2.title(s"${site.name}: $title"),
        HighlightJS.templateBlock(site.highlightJS),
        BuilderTemplate.managedScriptsBlock(linker, site.managedScripts),
        BuilderTemplate.managedStylesBlock(linker, site.managedStyles),
        templateStyles,
        searchScripts,
        searchStyles,
        meta(charset := "UTF-8")
      ),
      body(
        div(
          cls := "container",
          Header,
          NavigationBar(links),
          hr,
          content
        ),
        Footer
      )
    ).render
  }

  def NavigationBar(levels: Navigation) = {
    def renderNav(lst: Vector[NavLink], baseClass: String) = {
      lst.map { link =>
        val sel = if (link.selected) s" $baseClass-selected" else ""
        a(
          cls := s"$baseClass-btn" + sel,
          href := link.url,
          link.title
        )
      }
    }

    div(
      renderNav(levels.topLevel, "nav"),
      levels.sameLevel.map { sameLevel =>
        div(renderNav(sameLevel, "subnav"))
      }
    )
  }

  def Header =
    header(
      cls := "main-header",
      div(
        cls := "site-title",
        h1(
          a(href := linker.root, site.name)
        ),
        site.tagline.map { tagline => small(tagline) }
      ),
      div(id := "searchContainer", cls := "searchContainer"),
      div(
        cls := "site-links",
        site.githubUrl.map { githubUrl =>
          a(
            href := githubUrl,
            img(
              src := "https://cdn.svgporn.com/logos/github-icon.svg",
              cls := "gh-logo"
            )
          )
        }
      )
    )

  def Footer =
    footer(site.copyright)
}
