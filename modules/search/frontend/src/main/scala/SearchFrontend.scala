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
package search

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.scalajs.js
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import upickle.default.*

import SearchIndex.*

private[search] enum IndexState {
  case NotLoaded
  case Loading
  case Ready(search: Search)
  case Failed(reason: String)
}

/** What the results dropdown should show for the current query */
private[search] enum Outcome {
  case Idle
  case Pending
  case Unavailable
  case Found(shown: Vector[ResultsEntry], total: Int)
}

class SearchFrontend private (indexUrl: Option[String]) {
  import SearchFrontend.*

  private given ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.queue

  private val indexState = Var[IndexState](IndexState.NotLoaded)
  private val query      = Var("")
  private val open       = Var(false)

  /** Loads the index the first time it's needed (or after a failure). */
  private def ensureLoaded(): Unit =
    indexState.now() match {
      case IndexState.NotLoaded | IndexState.Failed(_) =>
        embeddedIndex match {
          case Some(text) => indexState.set(parse(text))
          case None       =>
            indexUrl match {
              case None =>
                indexState.set(IndexState.Failed("no search index URL"))
              case Some(url) =>
                indexState.set(IndexState.Loading)
                fetchText(url).onComplete {
                  case Success(text) => indexState.set(parse(text))
                  case Failure(err)  =>
                    dom.console.error(s"Failed to load search index: $err")
                    indexState.set(IndexState.Failed(err.getMessage()))
                }
            }
        }
      case _ => ()
    }

  private def fetchText(url: String): Future[String] =
    dom.fetch(url).toFuture.flatMap { response =>
      if (response.ok) response.text().toFuture
      else
        Future.failed(
          new RuntimeException(s"$url responded with ${response.status}")
        )
    }

  private def parse(text: String): IndexState =
    Try(new Search(read[SearchIndex](text))) match {
      case Success(search) => IndexState.Ready(search)
      case Failure(err)    =>
        dom.console.error(s"Failed to parse search index: $err")
        IndexState.Failed(err.getMessage())
    }

  private val outcome: Signal[Outcome] =
    query.signal.combineWith(indexState.signal).map {
      case (q, _) if q.trim.isEmpty     => Outcome.Idle
      case (q, IndexState.Ready(search)) =>
        val entries = search.string(q.trim).entries.map(_._1)
        Outcome.Found(entries.take(MaxResults), entries.size)
      case (_, IndexState.Failed(_)) => Outcome.Unavailable
      case _                         => Outcome.Pending
    }

  private val expanded: Signal[Boolean] =
    open.signal.combineWith(query.signal).map { case (isOpen, q) =>
      isOpen && q.trim.nonEmpty
    }

  def close(): Unit = open.set(false)

  private def reopen(): Unit = {
    ensureLoaded()
    open.set(true)
  }

  private val inputElement = input(
    idAttr       := InputId,
    typ          := "search",
    cls          := "sb-search-input",
    placeholder  := "Search...",
    autoComplete := "off",
    aria.label   := "Search",
    aria.controls := ResultsId,
    aria.describedBy := StatusId,
    onFocus --> { _ => reopen() },
    onClick --> { _ => reopen() },
    onInput --> { _ => reopen() },
    // The raw value is stored; trimming happens when searching, so typing a
    // space between words isn't undone.
    onInput.mapToValue.compose(_.debounce(DebounceMillis)) --> query,
    onKeyDown --> { ev =>
      ev.key match {
        case "ArrowDown" =>
          resultLinks().headOption.foreach { link =>
            ev.preventDefault()
            open.set(true)
            link.focus()
          }
        case "Escape" if open.now() =>
          ev.preventDefault()
          close()
        case _ => ()
      }
    }
  )

  private val resultsElement: HtmlElement = ul(
    idAttr     := ResultsId,
    cls        := "sb-search-results",
    aria.label := "Search results",
    display <-- expanded.map(if (_) "block" else "none"),
    children <-- outcome.map(renderOutcome),
    onKeyDown --> { ev =>
      val links   = resultLinks()
      val current = links.indexWhere(_ == dom.document.activeElement)

      ev.key match {
        case "ArrowDown" if current >= 0 =>
          ev.preventDefault()
          links.lift(current + 1).foreach(_.focus())
        case "ArrowUp" if current >= 0 =>
          ev.preventDefault()
          if (current == 0) inputElement.ref.focus()
          else links(current - 1).focus()
        case "Escape" =>
          ev.preventDefault()
          close()
          inputElement.ref.focus()
        case _ => ()
      }
    }
  )

  private def resultLinks(): Vector[dom.HTMLElement] = {
    val nodes = resultsElement.ref.querySelectorAll("a")
    (0 until nodes.length).map(nodes(_).asInstanceOf[dom.HTMLElement]).toVector
  }

  // Announced by screen readers; visually hidden
  private val statusElement = div(
    idAttr    := StatusId,
    role      := "status",
    styleAttr := VisuallyHidden,
    child.text <-- outcome.map {
      case Outcome.Idle        => ""
      case Outcome.Pending     => "Loading search index"
      case Outcome.Unavailable => "Search is unavailable"
      case Outcome.Found(shown, total) =>
        if (total == 0) "No results"
        else if (total == 1) "1 result"
        else if (shown.size < total)
          s"$total results, showing the top ${shown.size}"
        else s"$total results"
    }
  )

  val node: HtmlElement =
    div(
      cls  := "sb-search-container",
      role := "search",
      inputElement,
      statusElement,
      resultsElement,
      inContext { container =>
        def isInside(target: dom.EventTarget | Null) =
          target match {
            case n: dom.Node => container.ref.contains(n)
            case _           => false
          }

        Seq(
          // clicking anywhere else on the page closes the results
          documentEvents(_.onClick) --> { ev =>
            if (!isInside(ev.target)) close()
          },
          // so does moving keyboard focus out of the search
          eventProp[dom.FocusEvent]("focusout") --> { ev =>
            if (!isInside(ev.relatedTarget)) close()
          }
        )
      }
    )

  private def renderOutcome(outcome: Outcome): Seq[HtmlElement] =
    outcome match {
      case Outcome.Idle        => Nil
      case Outcome.Pending     => List(message("Loading…"))
      case Outcome.Unavailable => List(message("Search is unavailable"))
      case Outcome.Found(shown, _) if shown.isEmpty =>
        List(message("No results"))
      case Outcome.Found(shown, _) => shown.map(renderEntry)
    }

  private def message(text: String) =
    li(cls := "sb-search-message", text)

  /** Matched sections are the main links; the post they belong to is shown
    * underneath, smaller. A match on the post itself (its top section, which
    * shares the post's URL) is folded into that post link - unless it's the
    * only match, in which case the post becomes the main link.
    */
  private def renderEntry(entry: ResultsEntry): HtmlElement = {
    val ResultsEntry(document, sections) = entry
    val otherSections = sections.filterNot(_.url == document.url)

    if (otherSections.isEmpty)
      li(
        cls := "sb-search-result-container",
        renderSections(List(SectionEntry(document.title, document.url)))
      )
    else
      li(
        cls := "sb-search-result-container",
        renderSections(otherSections),
        a(
          cls  := "sb-search-result-document-url",
          href := document.url,
          document.title
        )
      )
  }

  private def renderSections(sections: List[SectionEntry]) =
    ul(
      cls := "subatomic-search-sections-container",
      sections.map { section =>
        li(
          a(
            cls  := "subatomic-search-section-url",
            href := section.url,
            section.title
          )
        )
      }
    )
}

@js.annotation.JSExportTopLevel("SubatomicSearchFrontend")
object SearchFrontend extends LaminarApp("sb-search-container") {

  /** At most this many documents are shown */
  final val MaxResults = 10

  /** Searching waits until typing pauses for this long */
  final val DebounceMillis = 120

  private final val InputId   = "sb-search-input"
  private final val ResultsId = "sb-search-results"
  private final val StatusId  = "sb-search-status"

  private final val VisuallyHidden =
    "position:absolute;width:1px;height:1px;padding:0;margin:-1px;" +
      "overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0"

  /** Attribute on the mount point holding the URL of search-index.json */
  final val IndexUrlAttribute = "data-index-url"

  private var current = Option.empty[SearchFrontend]

  /** Index embedded by older templates as a global `SearchIndexText` */
  private def embeddedIndex: Option[String] =
    if (js.typeOf(js.Dynamic.global.SearchIndexText) == "string")
      Some(js.Dynamic.global.SearchIndexText.asInstanceOf[String])
    else None

  def app = {
    val indexUrl =
      Option(root.getAttribute(IndexUrlAttribute)).filter(_.nonEmpty)
    val frontend = new SearchFrontend(indexUrl)

    current = Some(frontend)

    frontend.node
  }

  /** Hide the results dropdown */
  @js.annotation.JSExport
  def hideResults(): Unit = current.foreach(_.close())

  /** Deprecated alias of [[hideResults]], kept for templates that still call
    * it. (Not `@deprecated`: the JS export calls it, which would warn.)
    */
  @js.annotation.JSExport
  def sayHello(): Unit = hideResults()
}
