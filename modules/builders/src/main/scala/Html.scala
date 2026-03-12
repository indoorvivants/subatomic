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

package subatomic.builders

import scalatags.Text.TypedTag
import scalatags.Text.all._

object Html {

  def renderTOC(toc: TOC) = {

    div(
      cls := "sb-toc-container", {
        def render(toc: TOC): Option[TypedTag[String]] = {
          if (toc.level.nonEmpty)
            Some(
              ul(
                cls := "sbt-toc-list-container",
                toc.level.map { case (h, nest) =>
                  li(
                    a(
                      cls  := "sbt-toc-list-link",
                      href := s"#${h.anchorId}",
                      h.title
                    ),
                    render(nest)
                  )
                }
              )
            )
          else None
        }
        render(toc)
      }
    )
  }

}
