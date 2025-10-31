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

import scala.scalajs.js

import com.raquo.laminar.api.L._
import upickle.default._

import SearchIndex._

class SearchFrontend private (idx: SearchIndex) {
  val search           = new Search(idx)
  def query(s: String) = search.string(s)

  val node = {
    val queryVar = Var("")
    val ip       = input(
      value <-- queryVar.signal,
      cls         := "subatomic-search-input",
      placeholder := "Search...",
      onInput.mapToValue.map(_.trim) --> queryVar
    )

    val results = queryVar.signal.map(query)

    div(
      cls := "subatomic-search-container",
      ip,
      ul(
        queryVar.signal.map(_.isEmpty) --> SearchFrontend.HideResults,
        display <-- SearchFrontend.HideResults.signal
          .map(if (_) "none" else "block"),
        cls := "subatomic-search-results",
        children <-- results.map { results =>
          results.entries.map { case (ResultsEntry(document, sections), _) =>
            sections.find(o =>
              o.title == document.title && o.url == document.url
            ) match {
              case None =>
                div(
                  cls := "subatomic-search-result-container",
                  onClick.stopPropagation --> { _ => },
                  span(
                    cls := "subatomic-search-result-document-title",
                    document.title
                  ),
                  renderSections(sections)
                )

              case Some(oneSection) =>
                div(
                  cls := "subatomic-search-result-container",
                  onClick.stopPropagation --> { _ => },
                  a(
                    cls  := "subatomic-search-result-document-url",
                    href := document.url,
                    document.title
                  ),
                  renderSections(sections.filterNot(_ == oneSection))
                )
            }
          }
        }
      )
    )
  }

  def renderSections(sections: List[SectionEntry]) = {
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
}
@js.annotation.JSExportTopLevel("SubatomicSearchFrontend")
object SearchFrontend extends LaminarApp("searchContainer") {
  def load(s: String) = new SearchFrontend(read[SearchIndex](s))

  def app = {

    val frontend = load(js.Dynamic.global.SearchIndexText.asInstanceOf[String])

    div(frontend.node)
  }

  @js.annotation.JSExport
  def sayHello(): Unit = {
    HideResults.set(true)
  }

  val HideResults = Var(false)
}
