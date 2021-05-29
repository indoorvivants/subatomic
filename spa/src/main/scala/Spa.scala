package subatomic.spa

import com.raquo.laminar.api.L
import com.raquo.airstream.web.AjaxEventStream
import scalajs.js.JSON
import scalajs.js
import scalajs.js.JSStringOps._
import org.scalajs.dom
import L._

object SubatomicSPA {
  import com.raquo.waypoint._

  case class Site(
      pages: js.Array[(Page, String)],
      title: String,
      subtitle: Option[String],
      githubUrl: Option[String]
  ) {
    private val mapping = pages.map { case (p, title) => p.path.toList -> title }.toMap

    println(s"Mapping: $mapping")

    def pageTitle(pg: Page) = {
      mapping.apply(pg.path.toList)

    }
  }

  case class Page(path: js.Array[String]) {
    def sameAs(other: Page) = 
      path.sameElements(other.path)
  }

  def pageToString(pg: Page): String = pg.path.mkString("/")

  def fromString(str: String): Page = {
    Page(str.jsSplit("/"))
  }

  def transform(dyn: js.Dynamic): Option[Site] = {
    import scalajs.js.isUndefined

    def optString(field: String): Option[String] = {
      val value = dyn.selectDynamic(field)
      if (isUndefined(value)) None else Some(value.asInstanceOf[String])
    }

    def reqString(field: String): String = {
      dyn.selectDynamic(field).asInstanceOf[String]
    }

    val pagesField = dyn.selectDynamic("pages")

    if (!isUndefined(pagesField)) {

      val pagesArray = pagesField.asInstanceOf[js.Array[js.Any]]

      val builder = js.Array[(Page, String)]()

      pagesArray.foreach { a =>
        val pg = a.asInstanceOf[js.Array[js.Any]]

        val title    = pg(0).asInstanceOf[String]
        val segments = pg(1).asInstanceOf[js.Array[String]]

        builder.addOne(Page(segments) -> title)
      }

      Some(Site(builder, reqString("title"), optString("subtitle"), optString("githubUrl")))

    } else None
  }

  @js.native
  trait HighlightJs extends js.Object {
    def highlightAll(): Unit = js.native
  }

  val hljs = js.Dynamic.global.hljs.asInstanceOf[HighlightJs]

  def reHighlight() = {
    hljs.highlightAll()
  }

  class SPASite(site: Site) {

    val pageRoute = Route[Page, String](
      encode = pageToString(_),
      decode = fromString(_),
      pattern = (root / segment[String] / endOfSegments),
      basePath = Route.fragmentBasePath
    )

    val indexRoute = Route.static(Page(js.Array("index")), root / endOfSegments)

    // TODO: shifted site root?

    def renderPage(page: Page) =
      div(
        inContext(thisNode =>
          AjaxEventStream
            .get(
              // TODO: site on a shifted path
              dom.window.location.origin.toString + page.path.toList
                .prepended("_pages")
                .prepended("/")
                .mkString("/") + ".html"
            )
            .map(_.responseText) --> { c =>
            thisNode.ref.innerHTML = c
            reHighlight()

            dom.document.title = site.pageTitle(page)
          }
        )
      )

    lazy val router =
      new Router[Page](
        routes = List(indexRoute, pageRoute),
        getPageTitle = p => site.pageTitle(p),
        serializePage = page => pageToString(page),
        deserializePage = pageStr => fromString(pageStr)
      )(
        $popStateEvent = L.windowEvents.onPopState,
        owner = L.unsafeWindowOwner
      )

    def magicLink(page: Page, text: String) =
      a(
        href := router.absoluteUrlForPage(page),
        onClick.preventDefault --> (_ => router.pushState(page)),
        text
      )

    def magicLink(page: Page, text: Element) =
      a(
        href := router.absoluteUrlForPage(page),
        onClick.preventDefault --> (_ => router.pushState(page)),
        text
      )

    val Navigation =
      aside(
        cls := "navigation",
        ul(
          site.pages.map {
            case (page, title) =>
              val text =
                if (page.sameAs(router.$currentPage.now()))
                  b(title)
                else span(title)

              li(magicLink(page, text))
          }
        )
      )

    val Header =
      header(
        cls := "main-header",
        div(
          cls := "site-title",
          h1(
            a(href := "#index", site.title)
          ),
          site.subtitle.map { tagline => small(tagline) }
        ),
        div(idAttr := "searchContainer", cls := "searchContainer"),
        div(
          cls := "site-links",
          site.githubUrl.map { githubUrl =>
            a(
              href := githubUrl,
              img(
                src := "https://cdn.svgporn.com/logos/github-icon.svg",
                cls := "gh-logo"
              )
            )
          }
        )
      )

    lazy val Content = article(
      child <-- router.$currentPage.map(renderPage)
    )

    lazy val app = div(
      Header,
      div(cls := "site", Navigation, div(cls := "container", Content))
    )

  }

  def main(args: Array[String]): Unit = {
    val siteConfig =
      js.Dynamic.global.SubatomicSiteConfig.asInstanceOf[String]

    val site = transform(JSON.parse(siteConfig))

    val Site = new SPASite(site.get)

    val _ = documentEvents.onDomContentLoaded.foreach { _ =>
      val _ = render(org.scalajs.dom.document.getElementById("app"), Site.app)
    }(unsafeWindowOwner)
  }
}
