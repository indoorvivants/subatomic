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

import Channel._

case class Channel(
    description: Description,
    title: Title,
    link: Link,
    items: List[Item],
    language: Option[Language],
    copyright: Option[Copyright],
    tags: List[String]
) {
  def addItems(items: Item*): Channel =
    this.copy(items = this.items ++ items)
}

object Channel {
  case class Title(value: String)       extends AnyVal
  case class Description(value: String) extends AnyVal
  case class Link(value: String)        extends AnyVal
  case class Copyright(value: String)   extends AnyVal
  case class Language(value: String)    extends AnyVal
  def create(title: Title, link: Link, description: Description) =
    Channel(
      title = title,
      link = link,
      description = description,
      items = Nil,
      language = None,
      copyright = None,
      tags = Nil
    )
}
