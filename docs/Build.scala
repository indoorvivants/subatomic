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

import subatomic._

import cats.implicits._
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
    val content  = Content(contentRoot)
    val markdown = Markdown(RelativizeLinksExtension(siteRoot.toRelPath))

    val linker = new Linker(content, siteRoot)

    val template = new Template(linker)

    val mdocProcessor =
      if (runMdoc)
        MdocProcessor.create[Doc]() {
          case Doc(_, markdown, deps) => MdocFile(markdown, deps.toSet)
        }
      else {
        Processor.simple[Doc, MdocResult[Doc]](doc => MdocResult(doc, doc.path))
      }

    def renderMarkdownPage(title: String, file: os.Path) = {
      val renderedMarkdown = markdown.renderToString(file)
      val renderedHtml     = template.main(title, renderedMarkdown)

      Page(renderedHtml)
    }

    val mdocPageRenderer: Processor[Doc, SiteAsset] = mdocProcessor
      .map { mdocResult => renderMarkdownPage(mdocResult.original.title, mdocResult.resultFile) }

    Site
      .init(content)
      .populate {
        case (site, content) =>
          content match {
            case (sitePath, doc: Doc) =>
              site.addProcessed(sitePath, mdocPageRenderer, doc)
          }
      }
      .copyAll(contentRoot / "assets", SiteRoot / "assets")
      .addCopyOf(SiteRoot / "CNAME", contentRoot / "assets" / "CNAME")
      .buildAt(destination)

  }
}
