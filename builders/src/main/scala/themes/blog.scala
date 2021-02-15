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
package buildrs
package blog
package themes

import scalacss.DevDefaults._

object default extends StyleSheet.Standalone {
  import dsl._

  val whenOnWideScreen = media.screen.minDeviceWidth(1200.px)
  val whenOnPortrait   = media.screen.maxDeviceHeight(400.px).portrait

  "html, body" - (
    fontFamily.attr := "sans-serif",
    fontSize(16.px),
    height(100.%%),
    backgroundColor(c"#22333b")
  )

  "div.wrapper" - (
    whenOnWideScreen - (
      flexDirection.row,
      margin(30.px),
      display.grid,
      gridTemplateColumns := "minmax(10px, 1fr) minmax(10px, 5fr)",
      gap(1.rem),
      backgroundColor(c"#eae0d5"),
      maxWidth(80.%%),
      width(80.%%)
    ),
    whenOnPortrait - (
      display.flex,
      flexDirection.column,
      width(100.%%),
      maxWidth(100.%%)
    )
  )

  "a" - textDecorationLine.none

  "span.blog-tag" - (
    margin(3.px),
    backgroundColor(c"#d6d6d6"),
    padding(6.px),
    color.black,
    borderWidth(1.px, 1.px, 1.px, 5.px),
    borderColor.black,
    borderStyle.solid,
    display.inlineBlock
  )

  "span.blog-tag > a" - color.black

  "div.static-nav" - marginBottom(10.px)

  "div.about" - textAlign.right

  "img" - maxWidth(100.%%)

  "div.sidebar a:hover" - color(c"#eae0d5")

  "article.content-wrapper" - (
    backgroundColor(c"#eae0d5"),
    height(100.%%),
    height.auto,
    textAlign.justify,
    color(c"#0a0908"),
    whenOnWideScreen - (
      flexGrow(0),
      flexShrink(0),
      padding(10.px)
    ),
    whenOnPortrait - (
      padding(10.px),
      order(0),
      flexShrink(0),
      flexGrow(0),
      maxWidth(100.%%)
    )
  )

  "div.searchContainer a" - (
    color.darkslategrey
  )

  "div.sidebar" - (
    backgroundColor(c"#22333b"),
    color(c"#c6ac8f"),
    whenOnWideScreen - (
      flexShrink(0),
      flexGrow(0)
    ),
    whenOnPortrait - (
      padding(10.px),
      order(1),
      flexShrink(0),
      flexGrow(0)
    )
  )

  "div.blog-card div.blog-card-title" - (
    padding(10.px),
    backgroundColor(c"#436475"),
    color.white,
    textAlign.left
  )

  "div.blog-card-title a" - (
    fontSize(22.px),
    fontWeight.bold,
    textDecorationLine.none,
    color.white
  )

  "article.content-wrapper code" - (
    borderRadius(5.px),
    backgroundColor(c"#23241f"),
    padding(2.px),
    color.white
  )

  "article.content-wrapper" - fontSize(19.px)

  "article.content-wrapper code.hljs" - padding(10.px)

  def asString: String = this.renderA[String]
}
