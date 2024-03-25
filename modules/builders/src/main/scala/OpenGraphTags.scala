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
package builders

sealed abstract class OpenGraphTags(val label: String, val content: String)
object OpenGraphTags {
  case class Title(title: String) extends OpenGraphTags("og:title", title)
  case class Description(description: String)
      extends OpenGraphTags("og:description", description)
  case class Url(url: String) extends OpenGraphTags("og:url", url)

  sealed abstract class Type(value: String)
      extends OpenGraphTags("og:type", value)
  object Type {
    case object Article             extends Type("article")
    case object Website             extends Type("website")
    case class Other(value: String) extends Type(value)
  }

  def renderAsHtml(tags: OpenGraphTags) = {
    import scalatags.Text.all._
    import scalatags.Text.TypedTag

    meta(attr("property") := tags.label, attr("content") := tags.content)
  }

}
