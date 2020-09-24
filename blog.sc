import $file.markdown
import $file.render
import $file.site

import markdown.M
import render.R
import scalatags.Text.TypedTag
import scala.collection.immutable.SortedMap

import site.Site


sealed trait Tag
case object Scala extends Tag
case object ScalaMdoc extends Tag

sealed trait Content
case class BlogPost(
    title: String,
    file: os.Path,
    tags: Set[Tag] = Set.empty
) extends Content
case class MarkdownPage(title: String, file: os.Path) extends Content

val SiteRoot = os.RelPath("_site")

val Content: Vector[(os.RelPath, Content)] = Vector(
  { SiteRoot / "blog" / "google-search-history-analysis.html" } -> BlogPost(
    "Google search history analysis",
    os.pwd / "old_posts" / "google-search-history-analysis.md"
  ),
  { SiteRoot / "blog" / "test-mdoc.html" } -> BlogPost(
    "Test mdoc blog",
    os.pwd / "new_posts" / "test-mdoc.md",
    tags = Set(Scala, ScalaMdoc)
  ),
  { SiteRoot / "index.html" } -> MarkdownPage(
    "Home",
    os.pwd / "pages" / "index.md"
  )
)

object T {

  import scalatags.Text.all._
  import scalatags.Text.TypedTag

  def renderNav(navigation: Navigation) = {
    div(
      navigation.links.map {

        case (title, _, selected) if selected =>
          p(strong(title))
        case (title, url, _) =>
          p(a(href := url, title))

      }
    )
  }

  def Page(navigation: Navigation, content: TypedTag[_]) =
    html(
      head(
        scalatags.Text.tags2.title("My blog"),
        link(
          rel := "stylesheet",
          href := "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/10.2.0/styles/monokai-sublime.min.css"
        ),
        script(
          src := "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/10.2.0/highlight.min.js"
        ),
        script(
          src := "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/10.2.0/languages/r.min.js"
        ),
        script(
          raw("hljs.initHighlightingOnLoad();")
        ),
        scalatags.Text.tags2.style(
          raw("""
                div.container {
                    width: 70%;
                    margin: 30px;
                }
            """)
        )
      ),
      body(
        renderNav(navigation),
        div(
          div(cls := "container", content)
        )
      )
    )

  def BlogPage(
      navigation: Navigation
  )(title: String, tags: Iterable[String])(content: TypedTag[_]) = {
    Page(navigation, div(h1(title), p(tags.mkString(", ")), content))
  }

}

type Title = String
type URL = String
type Selected = Boolean
case class Navigation(links: List[(Title, URL, Selected)])

val navigationState: Function2[os.RelPath, Content, Navigation] = {
  val titles = Content.collect {
    case (rp, BlogPost(title, file, tags)) =>
      rp.toString() -> ("Blog: " + title)
    case (rp, MarkdownPage(title, file)) => rp.toString() -> title
  }.toList

  val titlePlusContent: List[((Title, URL), Content)] =
    titles.zip(Content.map(_._2))

  (relPath, content) => {
    Navigation(titlePlusContent.map {
      case ((rp, contentTitle), c) =>
        (contentTitle, "/" + rp.toString, content == c)
    })
  }
}

val watches = Content
  .map(_._2)
  .collect {
    case BlogPost(_, file, tags) => file
    case MarkdownPage(_, file)   => file
  }
  .map(interp.watch)

def buildSite =
  Site.components1[Content, Navigation](Content, navigationState) {
    case (_, BlogPost(title, file, tags), navigation) =>
      T.BlogPage(navigation)(title, tags.map(_.toString()))(
        R.renderMarkdown(M.trueRender(file))
      ).render

    case (_, MarkdownPage(title, file), navigation) =>
      T.Page(navigation, R.renderMarkdown(M.trueRender(file))).render
  }

buildSite

