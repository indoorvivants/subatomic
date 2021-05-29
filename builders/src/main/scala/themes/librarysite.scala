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
package librarysite
package themes

import scalacss.DevDefaults._

object default extends StyleSheet.Standalone {
  import dsl._

  "body" - (
    fontSize(16.px),
    color(black),
    backgroundColor(c"#463F3A"),
    fontFamily.attr := "-apple-system,BlinkMacSystemFont,Segoe UI,Helvetica,Arial,sans-serif,Apple Color Emoji,Segoe UI Emoji,Segoe UI Symbol"
  )

  "div.container" - (
    backgroundColor(rgb(254, 255, 248)),
    borderRadius(5.px),
    padding(30.px),
    width(70.%%),
    // margin(30.px),
    media.screen.minWidth(1600.px) - (
      width(50.%%),
      marginLeft(25.%%)
    )
  )

  "aside.navigation" - (
    maxWidth(200.px)
  )

  "aside.navigation a" - (
    color(rgb(254, 255, 248)),
    textDecorationLine.none,
    maxWidth(200.px)
  )

  "div.site" - (
    display.flex,
    flexDirection.row,
    justifyContent.right
  )

  "footer" - (
    width(80.%%),
    textAlign.center,
    marginLeft(30.px),
    padding(10.px),
    fontWeight.bold,
    color(c"#f4f3ee")
  )

  def extra =
    styleS(
      color.rgb(29, 49, 49),
      textDecorationLine.none.important,
      outline.none
    )

  "a" - (
    color.rgb(2, 47, 47),
    &.visited - extra,
    &.hover - extra,
    &.active - extra,
    &.focus - extra,
  )

  "header h1" - (
    margin(0.px)
  )

  "img.gh-logo" - (
    width(30.px),
    marginLeft(20.px)
  )

  "div.log img" - (
    width(75.px)
  )

  "header.main-header" - (
    display.flex,
    width(100.%%),
    flexDirection.row,
    flexWrap.nowrap,
    marginBottom(10.px)
  )

  "a.nav-btn" - (
    padding(10.px),
    marginRight(5.px),
    borderRadius(3.px),
    display.inlineBlock,
    textDecorationLine.underline,
    fontSize(20.px)
  )

  "a.nav-selected" - (
    borderLeft(0.px),
    backgroundColor.darkslategray,
    color.whitesmoke
  )

  "a.subnav-btn" - (
    padding(5.px),
    marginRight(5.px),
    borderRadius(3.px),
    display.inlineBlock,
    textDecorationLine.underline,
    fontSize(17.px)
  )

  "a.subnav-selected" - (
    borderLeft(0.px),
    backgroundColor.darkslategrey,
    color.whitesmoke
  )

  "div.site-title" - (
    alignSelf.flexStart,
    flexGrow(2)
  )

  "div.site-links" - (
    alignSelf.flexEnd
  )

  "div.terminal" - (
    overflow.scroll,
    color.white,
    backgroundColor.black
  )

  "div.terminal pre" - (
    backgroundColor.black,
    color.white,
    padding(20.px)
  )

  def asString: String = this.renderA[String]
}
