package subatomic
package builders
package librarysite

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
