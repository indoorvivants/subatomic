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

package subatomic.builders.util.rss

import java.time.OffsetDateTime

case class Item(
    title: String,
    description: Option[String],
    content: String,
    publicationDate: Option[OffsetDateTime],
    link: String,
    tags: List[String]
)

object Item {
  def create(title: String, content: String, link: String): Item =
    Item(
      title = title,
      content = content,
      link = link,
      tags = List.empty,
      publicationDate = None,
      description = None
    )
}

case class Channel(
    items: List[Item],
    link: String,
    description: String,
    title: String,
    language: Option[String],
    copyright: Option[String],
    tags: List[String]
) {
  def addItems(items: Item*): Channel =
    this.copy(items = this.items ++ items)
  def render = <hello>this.link</hello>
}
