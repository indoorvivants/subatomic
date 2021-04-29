package subatomic.spa

import com.raquo.laminar.api.L
import com.raquo.airstream.web.AjaxEventStream
import scalajs.js.JSON
import scalajs.js
import scalajs.js.JSStringOps._
import org.scalajs.dom
import L._

case class Site(
    pages: js.Array[Page],
    title: String,
    subtitle: Option[String]
)

case class Page(path: js.Array[String], title: String)

object SubatomicSPA {
  import com.raquo.waypoint._

  def pageToString(pg: Page): String =
    JSON.stringify(js.Dynamic.literal(path = pg.path, title = pg.title))

  def fromString(s: String): Page = {
    val parsed = JSON.parse(s)
    val path   = parsed.selectDynamic("path").asInstanceOf[js.Array[String]]
    val title  = parsed.selectDynamic("title").asInstanceOf[String]

    Page(path, title)
  }

  val pageRoute = Route.fragmentOnly(
    encode = pageToString(_),
    decode = fromString(_),
    encodeUrl = (page: Page) => page.path.mkString("/"),
    pattern = fragment[String]
  )

  val indexRoute = Route.static(Page(js.Array("index"), ""), root / endOfSegments)

  // TODO: shifted site root?
  def configPath =
    dom.window.location.origin.toString + "/site.json"

  def readConfig = AjaxEventStream.get(configPath).map(resp => JSON.parse(resp.responseText))

  def transform(dyn: js.Dynamic): Option[Site] = {
    import scalajs.js.isUndefined
    val pagesField = dyn.selectDynamic("pages")
    val title      = dyn.selectDynamic("title").asInstanceOf[String]

    if (!isUndefined(pagesField)) {

      val pagesArray = pagesField.asInstanceOf[js.Array[js.Any]]

      val builder = js.Array[Page]()

      pagesArray.foreach { a =>
        val pg = a.asInstanceOf[js.Array[js.Any]]

        val title    = pg(0).asInstanceOf[String]
        val segments = pg(1).asInstanceOf[js.Array[String]]

        builder.addOne(Page(segments, title))
      }

      Some(Site(builder, title, None))

    } else None
  }

  @js.native
  trait HighlightJs extends js.Object {
    def highlightAll() = js.native
  }

  val hljs = js.Dynamic.global.hljs.asInstanceOf[HighlightJs]

  def reHighlight() = {
    hljs.highlightAll()
  }

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
        }
      )
    )

  val router = new Router[Page](
    routes = List(pageRoute, indexRoute),
    getPageTitle = _.title,
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

  var site = Var(Option.empty[Site])

  val navigation = div(
    child <-- site.signal.map {
      case None => i("loading...")
      case Some(site) =>
        div(
          ul(
            site.pages.map { page =>
              val text =
                if (page == router.$currentPage.now())
                  b(page.title)
                else span(page.title)

              li(magicLink(page, text))
            }
          ),
          h1(site.title)
        )
    }
  )

  val content = article(
    child <-- router.$currentPage.map(renderPage)
  )

  val app = div(
    readConfig.map(transform) --> site.writer,
    navigation,
    content
  )

  def main(args: Array[String]): Unit = {
    val _ = documentEvents.onDomContentLoaded.foreach { _ =>
      val _ = render(org.scalajs.dom.document.getElementById("app"), app)
    }(unsafeWindowOwner)
  }
}
