package subatomic
package builders

import cats.implicits._
import com.monovore.decline._
import subatomic.Discover.MarkdownDocument

import librarysite._
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
        disableMdoc: Boolean
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
        help = "where the static site will be generated"
      )
      .withDefault(os.temp.dir())

    val command = Command("build site", "builds the site")(
      (destination, disableMdoc).mapN(Config)
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

    extra(baseSite).buildAt(buildConfig.destination)

  }
}

case class VersionedLibrarySite(
)
