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
package builders.blog

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import subatomic.Discover.MarkdownDocument

import cats.implicits._
import com.monovore.decline._
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension

case class Blog(
    contentRoot: os.Path,
    assetsRoot: Option[os.Path] = None,
    base: SitePath = SiteRoot,
    name: String,
    copyright: Option[String] = None,
    githubUrl: Option[String] = None,
    tagline: Option[String] = None,
    customTemplate: Option[Template] = None
)

sealed trait Doc {
  val title: String
}

case class Post(
    title: String,
    path: os.Path,
    date: LocalDate,
    description: Option[String],
    tags: List[String],
    mdocConfig: Option[MdocConfig]
) extends Doc

case class MdocConfig(
    dependencies: List[String]
)

object MdocConfig {
  def from(attrs: Discover.YamlAttributes): Option[MdocConfig] = {
    val enabled      = attrs.optionalOne("scala.mdoc").getOrElse("false").toBoolean
    val dependencies = attrs.optionalOne("scala.mdoc.dependencies").map(_.split(",").toList).getOrElse(Nil)

    if (enabled) Some(MdocConfig(dependencies)) else None
  }
}

object Blog {

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
    def extra(site: Site[Doc]) = site

    def config: Blog

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
    val all = content.map {
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
      siteConfig: Blog,
      buildConfig: cli.Config,
      extra: Site[Doc] => Site[Doc]
  ) = {
    val content = Discover
      .someMarkdown(siteConfig.contentRoot) {
        case MarkdownDocument(path, filename, attributes) =>
          val date        = LocalDate.parse(attributes.requiredOne("date"))
          val tags        = attributes.optionalOne("tags").toList.flatMap(_.split(",").toList)
          val title       = attributes.requiredOne("title")
          val description = attributes.optionalOne("description")

          val sitePath = SiteRoot / (date.format(DateTimeFormatter.ISO_LOCAL_DATE) + "-" + filename + ".html")

          val mdocConfig = MdocConfig.from(attributes)

          val post = Post(
            title,
            path,
            date,
            description,
            tags,
            mdocConfig
          ): Doc

          sitePath -> post
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
        MdocProcessor.create[Post]() {
          case Post(_, path, _, _, _, Some(config)) => MdocFile(path, config.dependencies.toSet)
        }
      else {
        Processor.simple[Post, MdocResult[Post]](doc => MdocResult(doc, doc.path))
      }

    def renderMarkdownPage(title: String, file: os.Path, links: Vector[NavLink]) = {
      val renderedMarkdown = markdown.renderToString(file)
      val renderedHtml =
        template.post(
          links,
          title,
          Iterable.empty,
          renderedMarkdown
        ) //template.post(navigation() title, renderedMarkdown, links)

      Page(renderedHtml)
    }

    val mdocPageRenderer: Processor[Post, SiteAsset] = mdocProcessor
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
            case (sitePath, doc: Post) if doc.mdocConfig.nonEmpty =>
              site.addProcessed(sitePath, mdocPageRenderer, doc)
            case (sitePath, doc: Post) =>
              site.add(sitePath, renderMarkdownPage(doc.title, doc.path, navigation(doc)))
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

case class Default(site: Blog, linker: Linker) extends Template

// trait Template {
//   def site: Blog
//   def linker: Linker

//   import scalatags.Text.all._
//   import scalatags.Text.TypedTag

//   def RawHTML(rawHtml: String) = div(raw(rawHtml))

//   def doc(title: String, content: String, links: Vector[NavLink]): String =
//     doc(title, RawHTML(content), links)

//   def doc(title: String, content: TypedTag[_], links: Vector[NavLink]): String = {
//     html(
//       head(
//         scalatags.Text.tags2.title(s"${site.name}: $title"),
//         link(
//           rel := "stylesheet",
//           href := linker.unsafe(_ / "assets" / "highlight-theme.css")
//         ),
//         link(
//           rel := "stylesheet",
//           href := linker.unsafe(_ / "assets" / "styles.css")
//         ),
//         link(
//           rel := "shortcut icon",
//           `type` := "image/png",
//           href := linker.unsafe(_ / "assets" / "logo.png")
//         ),
//         script(src := linker.unsafe(_ / "assets" / "highlight.js")),
//         script(src := linker.unsafe(_ / "assets" / "highlight-scala.js")),
//         script(src := linker.unsafe(_ / "assets" / "script.js")),
//         script(src := linker.unsafe(_ / "assets" / "search-index.js")),
//         meta(charset := "UTF-8")
//       ),
//       body(
//         div(
//           cls := "container",
//           Header,
//           NavigationBar(links),
//           hr,
//           content
//         ),
//         Footer,
//         script(src := linker.unsafe(_ / "assets" / "search.js"))
//       )
//     ).render
//   }

//   def NavigationBar(links: Vector[NavLink]) =
//     div(
//       links.map { link =>
//         val sel = if (link.selected) " nav-selected" else ""
//         a(
//           cls := "nav-btn" + sel,
//           href := link.url,
//           link.title
//         )
//       }
//     )

//   def Header =
//     header(
//       cls := "main-header",
//       div(
//         cls := "site-title",
//         h1(
//           a(href := linker.root, site.name)
//         ),
//         site.tagline.map { tagline => small(tagline) }
//       ),
//       div(id := "searchContainer", cls := "searchContainer"),
//       div(
//         cls := "site-links",
//         site.githubUrl.map { githubUrl =>
//           a(
//             href := githubUrl,
//             img(src := "https://cdn.svgporn.com/logos/github-icon.svg", cls := "gh-logo")
//           )
//         }
//       )
//     )

//   def Footer =
//     footer(site.copyright)
// }

trait Template {

  def site: Blog
  def linker: Linker

  import scalatags.Text.all._
  import scalatags.Text.TypedTag

  def Nav(navigation: Vector[NavLink]) = {
    div(
      navigation.sortBy(_.title).map {
        case NavLink(title, _, selected) if selected =>
          p(strong(title))
        case NavLink(title, url, _) =>
          p(a(href := url, title))
      }
    )
  }

  def rawHtml(rawHtml: String) = div(raw(rawHtml))

  def stylesheet(name: String) =
    link(
      rel := "stylesheet",
      href := linker.unsafe(_ / "assets" / "styles" / name)
    )

  def scriptFile(name: String) =
    script(src := linker.unsafe(_ / "assets" / "scripts" / name))

  def basePage(navigation: Option[Vector[NavLink]], content: TypedTag[_]) = {
    val pageTitle = navigation
      .flatMap(_.find(_.selected))
      .map(_.title)
      .map(": " + _)
      .getOrElse("")

    html(
      head(
        scalatags.Text.tags2.title("Anton Sviridov" + pageTitle),
        stylesheet("monokai-sublime.min.css"),
        stylesheet("site.css"),
        scriptFile("highlight.min.js"),
        scriptFile("r.min.js"),
        scriptFile("scala.min.js"),
        scriptFile("blog.js"),
        meta(charset := "UTF-8"),
        meta(
          name := "viewport",
          attr("content") := "width=device-width, initial-scale=1"
        )
      ),
      body(
        div(
          cls := "wrapper",
          div(
            cls := "sidebar",
            h2(a(href := linker.root, "Indoor Vivants")),
            hr,
            about,
            staticNav,
            h4("projects"),
            projectsNav,
            hr,
            h4("tags"),
            // tagCloud(tags),
            navigation match {
              case Some(value) => div(hr, h4("posts"), Nav(value))
              case None        => div()
            }
          ),
          tag("article")(cls := "content-wrapper", content)
        )
      )
    )
  }

  def page(navigation: Vector[NavLink], content: TypedTag[_]) =
    basePage(Some(navigation), content)

  def post(
      navigation: Vector[NavLink],
      title: String,
      tags: Iterable[String],
      content: String
  ): String = post(navigation, title, tags, rawHtml(content))

  def post(
      navigation: Vector[NavLink],
      title: String,
      tags: Iterable[String],
      content: TypedTag[_]
  ) = {
    val tagline = tags.toList.map { tag =>
      span(
        cls := "blog-tag",
        a(href := linker.unsafe(_ / "tags" / s"$tag.html"), small(tag))
      )
    }
    page(navigation, div(h2(title), p(tagline), hr, content)).render
  }

  def tagPage(
      navigation: Vector[NavLink],
      tag: String,
      blogs: Iterable[Post]
  ) = {
    page(
      navigation,
      div(
        h3(span("Posts tagged with ", span(cls := "blog-tag", tag))),
        div(cls := "card-columns", blogs.map(blogCard).toVector)
      )
    )
  }

  def dateFormat(dt: LocalDate) = dt.format(DateTimeFormatter.ISO_DATE)

  def blogPostSummary(
      title: String,
      date: LocalDate,
      url: String
  ) = {
    li(h3(a(href := url, title)), dateFormat(date))
  }

  // def tagCloud(
  //     tagPages: Iterable[TagPage]
  // ) = {
  //   div(
  //     tagPages.toList.map { tagPage =>
  //       span(a(href := linker.resolve(tagPage), small(tagPage.tag.tag)), " ")
  //     }
  //   )
  // }

  def blogCard(
      blogPost: Post
  ) = {
    div(
      cls := "blog-card",
      div(
        cls := "blog-card-body",
        div(
          cls := "blog-card-title",
          a(href := linker.find(blogPost), blogPost.title)
        ),
        p(cls := "blog-card-text", blogPost.description)
      )
    )
  }

  def indexPage(
      blogs: Iterable[Post]
  ) = {
    val (archived, modern) = blogs.partition(_.date.getYear < 2020)
    val newStuff =
      if (modern.nonEmpty)
        div(cls := "card-columns", modern.map(blogCard).toVector)
      else
        div(
          "Of course I spent all this time writing a static site generator and haven't actually written" +
            "a single blog post..."
        )

    basePage(
      None,
      div(
        h3("Blog posts"),
        newStuff,
        h3(cls := "text-muted", "Old posts"),
        div(cls := "card-columns", archived.map(blogCard).toVector)
      )
    )
  }

  def about =
    div(
      strong("Anton Sviridov"),
      p(
        "I love reinventing the wheel and I usually use Scala for that."
      )
    )

  def staticNav =
    ul(
      li(
        a(
          href := "https://github.com/keynmol/",
          "Github (personal)"
        )
      ),
      li(
        a(
          href := "https://twitter.com/velvetbaldmime/",
          "Tweettor"
        )
      )
    )

  def projectsNav =
    div(
      a(
        "Subatomic - barely a static site generator",
        href := "https://subatomic.indoorvivants.com/"
      )
    )
}
