package subatomic.spa

import com.raquo.laminar.api.L
import com.raquo.airstream.web.AjaxEventStream
import scalajs.js.JSON
import scalajs.js
import scalajs.js.JSStringOps._
import org.scalajs.dom
import L._

case class Site(
    pages: js.Array[(Page, String)]
)

case class Page(path: js.Array[String])

object SubatomicSPA {
  import com.raquo.waypoint._

  val pageRoute = Route.fragmentOnly(
    encode = (pg: Page) => pg.path.mkString("/"),
    decode = (arg: String) => Page(arg.jsSplit("/")),
    pattern = fragment[String]
  )

  val indexRoute = Route.static(Page(js.Array("index")), root / endOfSegments)
  
  // TODO: shifted site root?
  def configPath =
    dom.window.location.origin.toString + "/site.json"

  def readConfig = AjaxEventStream.get(configPath).map(resp => JSON.parse(resp.responseText))

  def transform(dyn: js.Dynamic): Option[Site] = {
    import scalajs.js.isUndefined
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

      Some(Site(builder))

    } else None
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
        }
      )
    )

  val router = new Router[Page](
    routes = List(pageRoute, indexRoute),
    getPageTitle = _.toString,
    serializePage = page => JSON.stringify(page.path),
    deserializePage = pageStr => Page(JSON.parse(pageStr).asInstanceOf[js.Array[String]])
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
        ul(
          site.pages.map {
            case (page, title) =>
              val text =
                if (page == router.$currentPage.now())
                  b(title)
                else span(title)

              li(magicLink(page, text))
          }
        )
    }
  )

  val content = article(
    child <-- router.$currentPage.map(renderPage)
  )

  val app = div(
    readConfig.map(transform) --> site.writer,
    child.text <-- site.signal.map(_.toString),
    navigation,
    content
  )

  def main(args: Array[String]): Unit = {
    val _ = documentEvents.onDomContentLoaded.foreach { _ =>
      val _ = render(org.scalajs.dom.document.getElementById("app"), app)
    }(unsafeWindowOwner)
  }
}
