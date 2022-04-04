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

case class RSS(version: String = "2.0", channel: Channel) {
  def render = {
    import scalatags.Text
    import Text.all._
    val t = (n: String) => Text.tags.tag(n)

    val document = t("rss")(attr("version") := "2.0")(
      t("channel")(
        t("title")(channel.title.value),
        t("link")(channel.link.value),
        t("description")(channel.description.value),
        for (item <- channel.items) yield {
          t("item")(
            t("title")(item.title.value),
            t("link")(item.link.value),
            item.description.map(d => t("description")(d.value)),
            item.publicationDate.map(odt =>
              t("pubDate")(
                odt.format(
                  java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                )
              )
            )
          )
        }
      )
    )

    """<?xml version="1.0"?>""" ++ document.render
  }
}
