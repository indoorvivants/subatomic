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

  val whenOnWideScreen   = media.screen.minWidth(1600.px)
  val whenOnNarrowScreen = media.screen.minWidth(1024.px).maxWidth(1600.px)
  val whenOnMobile       = media.screen.maxWidth(1023.px)

  def sidebarColor = c"#131c21"
  // def sidebarColor           = c"#000"
  def bodyColor              = articleColor
  def articleColor           = c"#FDFCFB"
  def articleTextColor       = c"#000"
  def blogTagBackgroundColor = c"#d6d6d6"
  def articleLinkColor       = c"#22333b"
  def sidebarLinkColor       = c"#eae0d5"
  def sidebarTextColor       = c"#c6ac8f"

  "html, body" - (
    fontFamily.attr := "sans-serif",
    fontSize(19.px),
    padding(0.px),
    margin(0.px),
    height(100.%%),
    minHeight(100.%%),
    backgroundColor(bodyColor)
  )

  "div.wrapper" - (
    height.auto,
    minHeight(100.%%),
    margin(0.px),
    display.block,
    whenOnWideScreen - (
      flexDirection.row,
      display.flex,
      width(100.%%),
      justifyContent.center,
      flexWrap.nowrap,
      alignContent.center
    ),
    whenOnNarrowScreen - (
      flexDirection.rowReverse,
      display.flex,
      maxWidth(1600.px)
    ),
    whenOnMobile - (maxWidth(100.%%), flexDirection.columnReverse, display.flex)
  )

  "span.blog-tag" - (
    margin(3.px),
    backgroundColor(blogTagBackgroundColor),
    padding(2.px),
    color.black,
    borderWidth(1.px, 1.px, 1.px, 5.px),
    borderColor.black,
    borderStyle.solid,
    display.inlineBlock,
    paddingLeft(5.px)
  )

  "span.blog-tag a, span.blog-tag a:hover" - (color.black, textDecorationLine.none)

  "img" - maxWidth(100.%%)

  "aside.sidebar a" - color(sidebarLinkColor)

  "aside.sidebar a:hover" - color(sidebarLinkColor)

  "article.content-wrapper" - (width(35.em), padding(30.px))

  "article.content-wrapper a" - (
    color(articleLinkColor),
    textDecorationLine.underline
  )
  "article.content-wrapper a:hover" - (
    color(articleLinkColor),
    textDecorationLine.none
  )

  "article.content-wrapper" - (
    backgroundColor(articleColor),
    height.auto,
    color(articleTextColor),
    whenOnWideScreen - (
      flexGrow(0),
      flexShrink(0)
    )
  )

  "div.searchContainer a" - (
    color.darkslategrey
  )

  "aside.sidebar" - (
    backgroundColor(sidebarColor),
    color(sidebarTextColor),
    padding(10.px),
    height.auto,
    whenOnWideScreen - (
      flexShrink(0),
      flexGrow(0),
      width(300.px),
      maxWidth(300.px)
    ),
    whenOnNarrowScreen - (
      maxWidth(300.px),
      width(300.px)
    )
  )

  "div.blog-card-title a" - (
    fontSize(2.5.rem),
    fontWeight.bold
  )

  "p.blog-card-text" - (
    marginTop(0.px),
    fontSize(1.5.rem)
  )

  "article.content-wrapper" - (
    fontSize(19.px),
    lineHeight(1.4),
    padding(20.px),
    whenOnWideScreen - (
      flexGrow(4),
      maxWidth(1000.px)
    ),
    whenOnMobile - (maxWidth(100.%%), padding(10.px)),
    whenOnNarrowScreen - (flexGrow(4), maxWidth(1000.px))
  )

  "blockquote" - (
    fontSize(120.%%),
    fontStyle.italic,
    borderLeftColor.darkgrey,
    borderLeftWidth(5.px),
    borderLeftStyle.solid,
    paddingLeft(1.em)
  )

  "a.heading-link" - (
    textDecorationLine.none
  )

  def asString: String = this.renderA[String]
}
