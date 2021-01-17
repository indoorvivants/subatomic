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
    val ip = input(placeholder := "Search...")
    val $stream = ip
      .events(onInput)
      .mapTo(ip.ref.value.trim())
      .startWith("")

    val $results = $stream.map(query)

    div(
      cls := "searchWrapper",
      ip,
      span(
        cls := "searchResults",
        display <-- $results.map(_.entries.nonEmpty).map(if (_) "block" else "none"),
        ul(
          children <-- $results.map { results =>
            println(results)
            results.entries.map {
              case (ResultsEntry(document, all @ (oneSection :: others)), _) =>
                if (oneSection.title == document.title && oneSection.url == document.url)
                  p(
                    b(a(href := document.url, document.title)),
                    renderSections(others)
                  )
                else {
                  p(
                    b(document.title),
                    renderSections(all)
                  )
                }
              case _ => i("no results")
            }
          }
        )
      )
    )
  }

  def renderSections(sections: List[SectionEntry]) = {
    ul(
      sections.map { section =>
        li(
          span(
            a(href := section.url, section.title)
          )
        )
      }
    )
  }
}

object SearchFrontend extends LaminarApp("searchContainer") {
  def load(s: String) = new SearchFrontend(read[SearchIndex](s))

  def app = {

    val frontend = load(js.Dynamic.global.SearchIndexText.asInstanceOf[String])

    div(frontend.node)
  }
}
