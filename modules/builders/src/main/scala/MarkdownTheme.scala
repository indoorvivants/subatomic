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

trait MarkdownTheme {
  import WithClassname.none

  object UnorderedList {
    var Container = none
    var Item      = none
  }
  object OrderedList {
    var Container = none
    var Item      = none
  }
  var Link         = none
  var Paragraph    = none
  var Quote        = none
  var Preformatted = none
  var Code         = none
  var InlineCode   = none

  object Headings {
    var H1 = none
    var H2 = none
    var H3 = none
    var H4 = none
    var H5 = none
  }

  object TableOfContents {
    var Container = none
    var List      = none
    var Link      = none
  }
}

object MarkdownTheme {

  object none    extends MarkdownTheme
  object default extends Default

  trait Default extends MarkdownTheme {
    import WithClassname.{apply => c}

    val headingTheme =
      s"underline hover:no-underline"
    Paragraph = c("leading-relaxed text-lg my-4 break-words w-full")
    val linkTheme = "underline hover:no-underline text-sky-700"
    Link = c(linkTheme)
    Preformatted = c("whitespace-pre-wrap")
    Code = c("whitespace-pre-wrap")
    UnorderedList.Container = c("list-disc mx-4 block text-lg")
    UnorderedList.Item = c("break-words")
    OrderedList.Container = c("list-decimal mx-4 block text-lg")
    OrderedList.Item = c("break-words")
    Headings.H1 = c(s"text-2xl font-bold my-2 $headingTheme")
    Headings.H2 = c(s"text-xl font-bold my-2 $headingTheme")
    Headings.H3 = c(s"text-lg font-bold my-2 $headingTheme")
    Headings.H4 = c(s"text-base font-bold my-2 $headingTheme")
    Headings.H5 = c(s"text-base font-bold my-2 $headingTheme")
    Quote = c("p-4 text-slate-700 border-l-4")
    InlineCode = c(
      "px-1 bg-slate-600 text-white rounded break-words"
    )

    TableOfContents.Container = c(
      "float-right p-3 rounded-md border-2 border-sky-700 max-w-12 m-4"
    )
    TableOfContents.List = c("list-disc my-0 mx-2 text-sm")
    TableOfContents.Link = c(s"${linkTheme} text-base")
  }

}
