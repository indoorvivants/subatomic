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
package blog
package themes

trait Theme {
  val c = WithClassname.apply(_)
  import WithClassname.none

  var Body: WithClassname      = none
  var Container: WithClassname = none
  var Main: WithClassname      = none
  object Aside {
    var Container = none
    object Section {
      var Container = none
      var Title     = none
      var Content   = none
    }

    var NavLink      = none
    var NavCurrent   = none
    var NavContainer = none

    object StaticLinks {
      var Container: WithClassname = none
      var Link: WithClassname      = none
    }
  }
  var Tag = none

  object TagCloud {
    var Container = none
    var Tag       = none
  }
  object PostCard {
    var Container: WithClassname   = none
    var Body: WithClassname        = none
    var Title: WithClassname       = none
    var Date: WithClassname        = none
    var Description: WithClassname = none
  }
  object Logo {
    var Container: WithClassname = none
    var Title: WithClassname     = none
    var Subtitle: WithClassname  = none
  }

  object Post {
    var Container: WithClassname   = none
    var Description: WithClassname = none
    var Title: WithClassname       = none
  }

  object TagPage {
    var Header: WithClassname = none
  }

  var Markdown: MarkdownTheme = MarkdownTheme.none
  var Search: SearchTheme     = SearchTheme.none

}

trait DefaultTheme extends Theme {
  PostCard.Container = c("p-6")
  Body = c("h-full min-h-screen")
  Container = c(
    "flex flex-col-reverse sm:flex-col-reverse md:flex-row lg:flex-row min-h-screen lg:max-w-6xl m-auto"
  )
  PostCard.Title = c("font-bold text-2xl")
  PostCard.Date = c("m-2 text-sm italic")
  Tag = c(
    "text-sm border-slate-700 hover:bg-slate-900 hover:border-slate-900 hover:text-white border-2 " +
      " border-l-[6px] p-1 m-1 inline-block hover:no-underline"
  )
  TagCloud.Container = c("flex gap-1 flex-wrap")
  TagCloud.Tag = c("text-lg no-underline hover:underline text-slate-400")

  Aside.Container = c(
    "bg-slate-900 text-white pr-6 pl-4 py-4 border-r-8 border-slate-700 grow-0 flex flex-col gap-4 md:w-[300px] shrink-0"
  )
  Aside.NavContainer = c("flex flex-col gap-2 text-sm")
  Aside.NavLink = c("no-underline hover:underline")
  Aside.NavCurrent = c("text-amber-200")

  Aside.StaticLinks.Container = c("ml-2 flex flex-col gap-1 ")
  Aside.StaticLinks.Link = c(
    "text-sm border-b-2 border-slate-700 hover:underline hover:border-0"
  )

  Aside.Section.Content = c("ml-4")
  Aside.Section.Title = c("font-bold")

  Logo.Container = c(
    "rounded-lg bg-white text-2xl p-4 text-black block w-full"
  )
  Logo.Title = c("font-bold text-center block")
  Logo.Subtitle = c("italic text-center text-sm block")

  TagPage.Header = c("p-4 text-xl")

  Post.Container = c("p-4 flex flex-col space-y-3")
  Post.Description = c("text-base underline")
  Post.Title = c("text-2xl m-2 font-bold")

  Markdown = MarkdownTheme.default
  Search = SearchTheme.default
}

object default extends DefaultTheme
