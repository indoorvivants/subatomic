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

  def sidebarColor           = c"#131c21"
  def bodyColor              = c"#fff9f2"
  def articleColor           = c"#eae0d5"
  def articleTextColor       = c"#0a0908"
  def blogTagBackgroundColor = c"#d6d6d6"
  def articleLinkColor       = c"#22333b"
  def sidebarLinkColor       = c"#eae0d5"
  def sidebarTextColor       = c"#c6ac8f"

  "html, body" - (
    fontFamily.attr := "sans-serif",
    fontSize(19.px),
    height(100.%%),
    padding(0.px),
    margin(0.px),
    backgroundColor(bodyColor)
  )

  "div.wrapper" - (
    height(100.%%),
    margin(0.px),
    whenOnWideScreen - (
      flexDirection.row,
      display.flex,
      width(100.%%),
      justifyContent.center,
      flexWrap.nowrap,
      alignContent.center,
    ),
    whenOnNarrowScreen - (
      flexDirection.rowReverse,
      display.flex,
      maxWidth(1600.px),
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

  "article.content-wrapper" - (width(35.em), padding(3.5.em))

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
    height(100.%%),
    height.auto,
    color(articleTextColor),
    whenOnWideScreen - (
      flexGrow(0),
      flexShrink(0),
    ),
  )

  "div.searchContainer a" - (
    color.darkslategrey
  )

  "aside.sidebar" - (
    backgroundColor(sidebarColor),
    color(sidebarTextColor),
    padding(10.px),
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
    fontSize(22.px),
    fontWeight.bold,
  )

  "p.blog-card-text" - (
    // marginLeft(20.px),
    marginTop(0.px),
    // paddingLeft(10.px),
    // borderLeftStyle.solid,
    // fontStyle.italic
    fontSize(17.px)
  )

  "article.content-wrapper" - (
    fontSize(19.px),
    lineHeight(1.4),
    padding(20.px),
    whenOnWideScreen - (
      flexGrow(4),
      maxWidth(1000.px),
    ),
    whenOnMobile - (maxWidth(100.%%), padding(3.px)),
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

  def asString: String = this.renderA[String]
}
