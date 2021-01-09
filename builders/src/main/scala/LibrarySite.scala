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

import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension
import subatomic.builders._
import subatomic.builders.librarysite.LibrarySite.Navigation

case class LibrarySite(
    override val contentRoot: os.Path,
    override val assetsRoot: Option[os.Path] = None,
    override val base: SitePath = SiteRoot,
    name: String,
    copyright: Option[String] = None,
    githubUrl: Option[String] = None,
    tagline: Option[String] = None,
    customTemplate: Option[Template] = None,
    links: Vector[(String, String)] = Vector.empty,
    override val highlightJS: HighlightJS = HighlightJS.default
) extends subatomic.builders.Builder(
      contentRoot = contentRoot,
      assetsRoot = assetsRoot,
      highlightJS = highlightJS,
      base = base
    )

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
        case Right(buildConfig) =>
          createSite(
            config,
            buildConfig,
            extra _
          )
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

  def createSite(
      siteConfig: LibrarySite,
      buildConfig: cli.Config,
      extra: Site[LibrarySite.Doc] => Site[LibrarySite.Doc]
  ) = {
    val content: Vector[(SitePath, Doc)] = Discover
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

    val markdown = Markdown(
      RelativizeLinksExtension(siteConfig.base.toRelPath),
      YamlFrontMatterExtension.create()
    )

    val linker = new Linker(content, siteConfig.base)

    val navigation = createNavigation(linker, content.map(_._2))

    val template =
      siteConfig.customTemplate.getOrElse(Default(siteConfig, linker))

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

    def addAllAssets(site: Site[Doc]) = {
      siteConfig.assetsRoot match {
        case Some(path) => site.copyAll(path, SiteRoot / "assets")
        case None       => site
      }
    }
    extra(addAllAssets(baseSite))
      .buildAt(buildConfig.destination, buildConfig.overwrite)
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
        link(
          rel := "shortcut icon",
          `type` := "image/png",
          href := linker.unsafe(_ / "assets" / "logo.png")
        ),
        script(src := linker.unsafe(_ / "assets" / "script.js")),
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
        Footer,
        script(src := linker.unsafe(_ / "assets" / "search.js"))
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
