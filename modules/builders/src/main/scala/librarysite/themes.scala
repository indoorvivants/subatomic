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
package librarysite

trait Theme {
  protected val c = WithClassname.apply(_)
  import WithClassname.{none}

  var Body: WithClassname      = none
  var Container: WithClassname = none
  var Main: WithClassname      = none
  var Footer: WithClassname    = none
  var Aside: WithClassname     = none

  object Header {
    var Container      = none
    var TitleContainer = none
    var Title          = none
    var Subtitle       = none
    var GithubUrl      = none
  }

  object Navigation {
    var Container: Int => WithClassname       = (_) => none
    var Link: (Int, Boolean) => WithClassname = (_, _) => none
  }

  var Markdown: MarkdownTheme = MarkdownTheme.none
  var Search: SearchTheme     = SearchTheme.none

}

trait DefaultTheme extends Theme {
  Body = c("bg-gradient-to-r from-emerald-800 to-sky-700 mt-4 w-full")
  val mainSizing = "m-2 mb-4 md:m-auto md:w-11/12 lg:w-7xl"
  Container = c(
    s"rounded-xl bg-white $mainSizing p-4 flex flex-col-reverse md:flex-row gap-6"
  )
  Aside = c(
    "lg:w-64 shrink-1 lg:shrink-0 grow-0 md:border-r-2 max-w-[300px] border-slate-200 pr-2"
  )
  Main = c("grow-4")
  Header.Title = c(s"block text-6xl text-white")
  Header.Subtitle = c("text-white")
  Header.Container = c(
    s"flex $mainSizing flex-col md:flex-row md:items-center justify-between mb-2"
  )
  Header.GithubUrl = c("w-12 opacity-70 hover:opacity-100")

  Navigation.Container = { i =>
    val base = "block"
    i match {
      case 0 => c(s"text-2xl p-4 $base")
      case 1 => c(s"text-xl p-2 $base")
      case _ => c(s"text-lg p-1 $base")
    }

  }

  Navigation.Link = { (_, selected) =>
    if (selected) c("font-bold") else c("")
  }

  Footer = c("text-center m-4 p-4 text-lg text-white font-bold m-auto w-full")
  Markdown = MarkdownTheme.default
  Search = SearchTheme.default
}

object default extends DefaultTheme
