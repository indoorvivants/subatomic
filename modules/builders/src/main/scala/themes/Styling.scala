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

package subatomic.builders.themes

trait WithClassname {
  def className: Option[String]
}
object WithClassname {
  private class Impl(value: Option[String]) extends WithClassname {
    def className: Option[String] = value
  }
  def apply(value: String): WithClassname = new Impl(Some(value))
  def none: WithClassname                 = new Impl(None)

}

trait MarkdownTheme {
  import WithClassname.none

  object UnorderedList {
    var Container = none
    var Item      = none
  }
  object OrderedList {
    var Container = none
    var Item      = none
  }
  var Link         = none
  var Paragraph    = none
  var Quote        = none
  var Preformatted = none
  var Code         = none

  object Headings {
    var H1 = none
    var H2 = none
    var H3 = none
    var H4 = none
    var H5 = none
  }
}

object MarkdownTheme {
  object none extends MarkdownTheme
}
