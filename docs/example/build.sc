import $ivy.`com.indoorvivants::subatomic:0.0.2`
import $ivy.`com.lihaoyi::scalatags:0.9.1`

import com.indoorvivants.subatomic._
import ammonite.ops._

@main
def main(siteRoot: String = "", destination: os.Path = os.pwd / "_site") = {
  val content = Content(os.pwd)

  interp.watch(os.pwd / "pages")

  createSite(
    destination,
    os.pwd,
    SitePath.fromRelPath(os.RelPath(siteRoot))
  )
}

def createSite(
    destination: os.Path,
    contentRoot: os.Path = os.pwd,
    siteRoot: SitePath
) = {
  // creating a full site map
  val raw = Content(contentRoot)

  val content = raw.map { case (rawLocation, content) =>
    rawLocation.prepend(siteRoot) -> content
  }

  // helper to resolve links to their correct
  // values with regard to site root
  val linker = new Linker(raw, siteRoot)

  // built-in Mdoc interface
  val mdocProc   = new MdocProcessor()
  val mdocJsProc = new MdocJsProcessor

  val template = new Template(linker)

  // wrapper around flexmark
  val markdown = Markdown(
    // optional:
    //   relativizes all  links in markdown
    //   relative to the path (in this case /)
    RelativizeLinksExtension(siteRoot.toRelPath)
  )

  Site.build(destination)(content) {
    // handling markdown pages
    case (path, MarkdownPage(title, markdownFile)) =>
      Some(
        Page(
          template.main(title, markdown.renderToString(markdownFile))
        )
      )

    case (_, ScalaPage(title, mdFile, deps)) =>
      val processed = mdocProc.process(os.pwd, mdFile, deps)
      Some(
        Page(
          template.main(title, markdown.renderToString(processed))
        )
      )

    // handling static assets
    case (_, sf: StaticFile) =>
      Some(CopyOf(sf.path))

    case (sitePath, ScalaJSPage(title, mdFile, deps)) =>
      val result = mdocJsProc.process(pwd, mdFile, deps)

      List[SiteAsset](
        Page(
          template.main(title, markdown.renderToString(result.mdFile))
        ),
        CreatedFile(result.mdjsFile, sitePath.up / result.mdjsFile.last),
        CreatedFile(result.mdocFile, sitePath.up / "mdoc.js")
      )
  }
}

sealed trait Content
case class ScalaPage(
    title: String,
    path: os.Path,
    dependencies: List[String]
) extends Content

case class ScalaJSPage(
    title: String,
    path: os.Path,
    dependencies: List[String]
) extends Content

case class StaticFile(path: os.Path)                  extends Content
case class MarkdownPage(title: String, path: os.Path) extends Content

object Content {
  def apply(root: os.Path) =
    Assets(root) ++ Pages(root)

  def Assets(root: os.Path): Vector[(SitePath, Content)] = {
    // recursively discovering all files in assets folder
    os.walk(root / "assets").filter(_.toIO.isFile()).map { path =>
      // make sure relative path on site matches relative path
      // in assets folder
      SiteRoot / path.relativeTo(root) -> StaticFile(path)
    }
  }.toVector

  def Pages(root: os.Path): Vector[(SitePath, Content)] = {
    Vector(
      SiteRoot / "index.html" -> MarkdownPage(
        "Home",
        root / "pages" / "index.md"
      ),
      SiteRoot / "scala-usage.html" -> ScalaPage(
        "Scala usage",
        root / "pages" / "scala-usage.md",
        List("org.typelevel::cats-effect:2.2.0")
      ),
      SiteRoot / "scala-js-usage.html" -> ScalaJSPage(
        "Scala.js usage",
        root / "pages" / "scala-js-usage.md",
        List("com.raquo::laminar_sjs1:0.11.0")
      )
    )
  }
}

class Template(linker: Linker) {
  import scalatags.Text.all._
  import scalatags.Text.TypedTag

  def RawHTML(rawHtml: String) = div(raw(rawHtml))

  def main(title: String, content: String): String =
    main(title, RawHTML(content))

  def main(title: String, content: TypedTag[_]): String = {
    html(
      head(
        scalatags.Text.tags2.title(title),
        link(
          rel  := "stylesheet",
          href := linker.rooted(_ / "assets" / "highlight-theme.css")
        ),
        link(
          rel  := "stylesheet",
          href := linker.rooted(_ / "assets" / "bootstrap.css")
        ),
        script(src := linker.rooted(_ / "assets" / "highlight.js")),
        script(src := linker.rooted(_ / "assets" / "highlight-scala.js")),
        script(src := linker.rooted(_ / "assets" / "script.js"))
      ),
      body(
        div(
          cls := "container",
          div(
            cls := "row",
            div(
              cls := "col-9",
              h1(title),
              content
            )
          )
        )
      )
    ).render
  }
}
