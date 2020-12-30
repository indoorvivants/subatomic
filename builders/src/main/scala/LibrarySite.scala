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

import cats.implicits._
import com.monovore.decline._
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension

case class LibrarySite(
    contentRoot: os.Path,
    assetsRoot: Option[os.Path] = None,
    base: SitePath = SiteRoot,
    name: String,
    copyright: Option[String] = None,
    githubUrl: Option[String] = None,
    tagline: Option[String] = None,
    customTemplate: Option[Template] = None
)

object LibrarySite {

  case class Doc(
      title: String,
      path: os.Path,
      inNavBar: Boolean,
      mdocDependencies: Set[String] = Set.empty
  )

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

  def createNavigation(linker: Linker, content: Vector[Doc]): Doc => Vector[NavLink] = {
    val all = content.filter(_.inNavBar).map {
      case doc => doc -> NavLink(linker.find(doc), doc.title, selected = false)
    }

    { piece =>
      all.map {
        case (`piece`, link) => link.copy(selected = true)
        case (_, link)       => link
      }
    }
  }

  def createSite(
      siteConfig: LibrarySite,
      buildConfig: cli.Config,
      extra: Site[LibrarySite.Doc] => Site[LibrarySite.Doc]
  ) = {
    val content = Discover
      .someMarkdown(siteConfig.contentRoot) {
        case MarkdownDocument(path, filename, attributes) =>
          val id       = attributes.requiredOne("id")
          val inNavBar = attributes.optionalOne("in_navigation_bar").map(_.toBoolean).getOrElse(true)
          val title    = attributes.requiredOne("title")

          val sitePath =
            if (filename != "index")
              SiteRoot / (path / os.up).relativeTo(siteConfig.contentRoot) / id / "index.html"
            else SiteRoot / (path / os.up).relativeTo(siteConfig.contentRoot) / "index.html"

          sitePath -> Doc(title, path, inNavBar)
      }
      .toVector

    val markdown = Markdown(
      RelativizeLinksExtension(siteConfig.base.toRelPath),
      YamlFrontMatterExtension.create()
    )

    val linker = new Linker(content, siteConfig.base)

    val navigation = createNavigation(linker, content.map(_._2))

    val template = siteConfig.customTemplate.getOrElse(Default(siteConfig, linker))

    val mdocProcessor =
      if (!buildConfig.disableMdoc)
        MdocProcessor.create[Doc]() {
          case doc => MdocFile(doc.path, doc.mdocDependencies)
        }
      else {
        Processor.simple[Doc, MdocResult[Doc]](doc => MdocResult(doc, doc.path))
      }

    def renderMarkdownPage(title: String, file: os.Path, links: Vector[NavLink]) = {
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
            case (sitePath, doc: Doc) =>
              site.addProcessed(sitePath, mdocPageRenderer, doc)
          }
      }

    extra(baseSite).buildAt(buildConfig.destination, buildConfig.overwrite)

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

  def doc(title: String, content: String, links: Vector[NavLink]): String =
    doc(title, RawHTML(content), links)

  def doc(title: String, content: TypedTag[_], links: Vector[NavLink]): String = {
    html(
      head(
        scalatags.Text.tags2.title(s"${site.name}: $title"),
        link(
          rel := "stylesheet",
          href := linker.unsafe(_ / "assets" / "highlight-theme.css")
        ),
        link(
          rel := "stylesheet",
          href := linker.unsafe(_ / "assets" / "styles.css")
        ),
        link(
          rel := "shortcut icon",
          `type` := "image/png",
          href := linker.unsafe(_ / "assets" / "logo.png")
        ),
        script(src := linker.unsafe(_ / "assets" / "highlight.js")),
        script(src := linker.unsafe(_ / "assets" / "highlight-scala.js")),
        script(src := linker.unsafe(_ / "assets" / "script.js")),
        script(src := linker.unsafe(_ / "assets" / "search-index.js")),
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

  def NavigationBar(links: Vector[NavLink]) =
    div(
      links.map { link =>
        val sel = if (link.selected) " nav-selected" else ""
        a(
          cls := "nav-btn" + sel,
          href := link.url,
          link.title
        )
      }
    )

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
            img(src := "https://cdn.svgporn.com/logos/github-icon.svg", cls := "gh-logo")
          )
        }
      )
    )

  def Footer =
    footer(site.copyright)
}
