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

package docs

import cats.implicits._
import com.indoorvivants.subatomic._
import com.monovore.decline._

object Main {
  object cli {
    case class Config(
        destination: os.Path,
        contentRoot: os.Path,
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

    private val contentRoot = Opts
      .option[os.Path](
        "content-root",
        help = "where the content is located"
      )
      .withDefault(os.pwd / "docs")

    val command = Command("build site", "builds the site")(
      (destination, contentRoot, disableMdoc).mapN(Config)
    )
  }

  def main(args: Array[String]): Unit = {
    import cli._

    command.parse(args.toList) match {
      case Left(value) => println(value)
      case Right(Config(destination, contentRoot, disableMdoc)) =>
        createSite(
          destination = destination,
          siteRoot = SiteRoot,
          contentRoot = contentRoot,
          runMdoc = !disableMdoc
        )

    }
  }

  def createSite(
      destination: os.Path,
      siteRoot: SitePath,
      contentRoot: os.Path,
      runMdoc: Boolean
  ) = {
    val content = Content(contentRoot)

    val linker = new Linker(content, siteRoot)

    val mdocProc = new MdocProcessor()
    val markdown = Markdown(RelativizeLinksExtension(siteRoot.toRelPath))

    val template = new Template(linker)

    val preparedMdoc = mdocProc.prepare(
      content.collect {
        case (_, d @ Doc(_, markdown, deps)) =>
          d -> MdocFile(markdown, deps.toSet)
      },
      pwd = Some(os.pwd)
    )

    Site.build(destination)(content) {
      case (_, doc @ Doc(title, markdownFile, _)) =>
        val processed =
          if (runMdoc) preparedMdoc.get(doc)
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
}

sealed trait Content extends Product with Serializable
case class Doc(
    title: String,
    path: os.Path,
    dependencies: Set[String] = Set.empty
)                                    extends Content
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

  def apply(root: os.Path): Vector[(SitePath, Content)] = {
    Vector(
      SiteRoot / "index.html" -> Doc(
        "Home ",
        root / "pages" / "index.md"
      ),
      SiteRoot / "example.html" -> Doc(
        "Example",
        root / "pages" / "example.md"
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
      img(
        src := linker.rooted(_ / "assets" / "logo.png"),
        cls := "img-fluid",
        height := "100"
      )
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
