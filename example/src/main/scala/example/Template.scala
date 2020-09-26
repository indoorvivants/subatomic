package example

object Template {

  import scalatags.Text.all._
  import scalatags.Text.TypedTag

  def Nav(navigation: Navigation) = {
    div(
      div(
        navigation.links.sortBy(_._1).map {

          case (title, _, selected) if selected =>
            p(strong(title))
          case (title, url, _) =>
            p(a(href := url, title))
        }
      ),
      hr,
      "Tags: ",
      navigation.tags.map {
        case (tg, url) =>
          strong(a(href := url, tg.s), " ")
      }
    )
  }

  def RawHTML(rawHtml: String) = div(raw(rawHtml))

  def Page(title: String, navigation: Navigation, content: TypedTag[_]) =
    html(
      head(
        scalatags.Text.tags2.title(title),
        link(
          rel := "stylesheet",
          href := "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/10.2.0/styles/monokai-sublime.min.css"
        ),
        link(
          rel := "stylesheet",
          href := "https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css",
          attr(
            "integrity"
          ) := "sha384-JcKb8q3iqJ61gNV9KGb8thSsNjpSL0n8PARn9HuZOnIxN0hoP+VmmDGMN5t9UJ0Z",
          attr("crossorigin") := "anonymous"
        ),
        script(
          src := "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/10.2.0/highlight.min.js"
        ),
        script(
          src := "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/10.2.0/languages/scala.min.js"
        ),
        script(
          raw("hljs.initHighlightingOnLoad();")
        )
      ),
      body(
        div(
          cls := "container-fluid",
          h1("Subatomic"),
          hr,
          div(
            cls := "row",
            div(cls := "col-3", Nav(navigation)),
            div(cls := "col-7", content)
          )
        )
      )
    )

  def BlogPage(
      navigation: Navigation,
      title: String,
      tags: Iterable[String],
      content: TypedTag[_]
  ) = {
    Page(title, navigation, div(h1(title), p(tags.mkString(", ")), content))
  }

  def TagPage(
      tag: example.Tag,
      links: Vector[(String, String)],
      nav: Navigation
  ) = {
    Page(
      s"Posts with tag '$tag'",
      navigation = nav,
      div(
        p("Content about ", strong(tag.s)),
        ul(
          links.map {
            case (title, link) =>
              li(a(href := link, title))
          }
        )
      )
    )
  }

}
