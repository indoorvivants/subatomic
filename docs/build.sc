import $ivy.`com.indoorvivants::subatomic:0.0.2`
import $ivy.`com.lihaoyi::scalatags:0.9.1`

import com.indoorvivants.subatomic._

@main
def main(
    version: String,
    destination: os.Path,
    siteRoot: String = "",
    contentRoot: os.Path = os.pwd,
    runMdoc: Boolean = true
) = {

  interp.watch(contentRoot / "pages")
  interp.watch(contentRoot / "assets")

  createSite(
    version,
    destination,
    SitePath.fromRelPath(os.RelPath(siteRoot)),
    contentRoot,
    runMdoc
  )
}

def createSite(
    version: String,
    destination: os.Path,
    siteRoot: SitePath,
    contentRoot: os.Path,
    runMdoc: Boolean
) = {
  val content = Content(contentRoot, version)

  val linker = new Linker(content, siteRoot)

  val mdocProc = new MdocProcessor()
  val markdown = Markdown(RelativizeLinksExtension(siteRoot.toRelPath))

  val template = new Template(linker)

  Site.build(destination)(content) {
    case (path, Doc(title, markdownFile, deps)) =>
      val processed =
        if (runMdoc) mdocProc.process(os.pwd, markdownFile, deps)
        else markdownFile

      Some(
        Page(
          template.main(title, markdown.renderToString(processed))
        )
      )

    case (_, sf: StaticFile) =>
      Some(CopyOf(sf.path))
  }
}

sealed trait Content extends Product with Serializable
case class Doc(title: String, path: os.Path, dependencies: List[String])
    extends Content
case class StaticFile(path: os.Path) extends Content

object Content {

  def assets(root: os.Path): Vector[(SitePath, Content)] = {
    os.walk(root / "assets").filter(_.toIO.isFile()).map { path =>
      if (path.endsWith(os.RelPath("CNAME")))
        SiteRoot / "CNAME" -> StaticFile(path)
      else
        SiteRoot / path.relativeTo(root) -> StaticFile(path)
    }
  }.toVector

  def apply(root: os.Path, version: String): Vector[(SitePath, Content)] = {

    val subatomic = s"com.indoorvivants::subatomic:$version"
    val scalatags = "com.lihaoyi::scalatags:0.9.1"

    Vector(
      SiteRoot / "index.html" -> Doc(
        "Home ",
        root / "pages" / "index.md",
        List(subatomic)
      ),
      SiteRoot / "example.html" -> Doc(
        "Example",
        root / "pages" / "example.md",
        List(subatomic, scalatags)
      )
    ) ++ assets(root)
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
        scalatags.Text.tags2.title(s"Subatomic: $title"),
        link(
          rel := "stylesheet",
          href := linker.rooted(_ / "assets" / "highlight-theme.css")
        ),
        link(
          rel := "stylesheet",
          href := linker.rooted(_ / "assets" / "bootstrap.css")
        ),
        link(
          rel := "stylesheet",
          href := linker.rooted(_ / "assets" / "styles.css")
        ),
        link(
          rel := "shortcut icon",
          `type` := "image/png",
          href := linker.rooted(_ / "assets" / "logo.png")
        ),
        script(src := linker.rooted(_ / "assets" / "highlight.js")),
        script(src := linker.rooted(_ / "assets" / "highlight-scala.js")),
        script(src := linker.rooted(_ / "assets" / "script.js")),
        meta(charset := "UTF-8")
      ),
      body(
        div(
          cls := "container",
          div(
            cls := "row",
            div(cls := "col-1"),
            div(
              cls := "col-9",
              Header,
              hr,
              a(
                cls := "btn btn-dark",
                href := linker.rooted(_ / "index.html"),
                "Home"
              ),
              " ",
              a(
                cls := "btn btn-dark",
                href := linker.rooted(_ / "example.html"),
                "Example"
              ),
              hr,
              h1(title),
              content
            )
          ),
          div(cls := "row", div(cls := "col-12", Footer))
        )
      )
    ).render
  }

  val Header = div(
    cls := "row",
    div(
      cls := "col-2",
      img(src := linker.rooted(_ / "assets" / "logo.png"), cls := "img-fluid", height := "100")
    ),
    div(
      cls := "col-8",
      div(
        cls := "align-middle",
        h1(a(href := linker.root, "Subatomic")),
        small("a tiny, horrible static site builder for Scala")
      )
    ),
    div(
      cls := "col-2",
      p(
        a(
          href := "https://github.com/indoorvivants/subatomic",
          "Github"
        )
      ),
      p(
        a(
          href := "https://index.scala-lang.org/indoorvivants/subatomic/subatomic",
          "Versions"
        )
      )
    )
  )

  val Footer = div(
    cls := "footer",
    "© 2020 Anton Sviridov"
  )
}
