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

import BuilderSteps._

class BuilderSteps(markdown: Markdown) {

  def addAllAssets[Doc](
      assetsRoot: Option[os.Path],
      assetsFilter: os.Path => Boolean,
      destination: SitePath = SiteRoot / "assets"
  ): Site[Doc] => Site[Doc] =
    site => {
      assetsRoot match {
        case Some(path) => site.copyAll(path, destination, assetsFilter)
        case None       => site
      }
    }

  def d2Step[Doc](
      d2: D2,
      diagrams: Map[SitePath, D2Extension.Diagram]
  ): Site[Doc] => Site[Doc] = { site =>
    site.addDelayedAssets(
      () =>
        diagrams.map { case (path, dg) =>
          path -> Page(d2.diagram(dg.code, dg.args))
        },
      "<D2 diagrams collected from markdown files>"
    )

  }

  def tailwindStep[Doc](
      destination: os.Path,
      tailwind: TailwindCSS,
      markdownTheme: MarkdownTheme,
      searchTheme: SearchTheme
  ): Site[Doc] => Site[Doc] = site => {

    site.addDelayedAsset(
      SiteRoot / "assets" / "tailwind.css",
      { () =>
        val allHtml     = os.walk(destination).filter(_.ext == "html")
        val allJs       = os.walk(destination).filter(_.ext == "js")
        val markdownCSS = renderMarkdownBase(markdownTheme)
        val searchCSS   =
          renderSearchTheme(searchTheme, ".subatomic-search-container")
        Page(tailwind.process(allHtml ++ allJs, s"$markdownCSS\n$searchCSS"))

      },
      "<generated and minified tailwind CSS"
    )
  }

  def buildSearchIndex[Doc](
      linker: Linker,
      d: PartialFunction[Doc, SearchableDocument]
  )(
      content: Vector[(SitePath, Doc)]
  ): subatomic.search.SearchIndex = {
    subatomic.search.Indexer.default(content).processSome {
      case (_, raw) if d.isDefinedAt(raw) =>
        val doc              = d(raw)
        val markdownSections =
          markdown
            .extractMarkdownSections(doc.title, linker.find(raw), doc.path)
        subatomic.search.Document(
          doc.title,
          linker.find(raw),
          markdownSections.map { case Markdown.Section(title, _, url, text) =>
            subatomic.search.Section(title, url, title + "\n\n" + text)
          }
        )
    }
  }

  def addSearchIndex[Doc](
      linker: Linker,
      d: PartialFunction[Doc, BuilderSteps.SearchableDocument],
      content: Vector[(SitePath, Doc)]
  ): Site[Doc] => Site[Doc] = {

    val indexJson      = buildSearchIndex(linker, d)(content).asJsonString
    val doubleQ        = '"'.toString()
    val escapedDoubleQ = """ \" """.trim()

    val lines = indexJson
      .grouped(500)
      .map(
        _.replace("'", raw"\'")
          .replace(doubleQ, escapedDoubleQ)
      )
      .map(str => s"'${str}'")
      .mkString(",\n")

    val tmpFile = os.temp {
      s"""
        var ln = [$lines];var SearchIndexText = ln.join('')
        """
    }

    val tmpFileJS = os.temp(search.SearchFrontendPack.fullJS)

    site =>
      site
        .addCopyOf(SiteRoot / "assets" / "search-index.js", tmpFile)
        .addCopyOf(SiteRoot / "assets" / "search.js", tmpFileJS)

  }

  private def renderSearchTheme(base: SearchTheme, selector: String) = {

    val applications                                 = Vector.newBuilder[String]
    def add(query: String, cls: WithClassname): Unit = {
      import ExtraStyles._
      cls match {
        case Define(name, es) =>
          es match {
            case CSS(value) =>
              applications += s"$query.$name { $value }"
            case TailwindApply(classes) =>
              applications += s"$query.$name { @apply $classes}"
          }

        case Stylesheet(definitions) =>
          definitions.foreach(add(query, _))

        case _ =>
          cls.className.filter(_.trim.nonEmpty).foreach { cls =>
            applications += s"$query { @apply $cls}"
          }
      }
    }

    add(s"$selector", base.Container)
    add(s"$selector input.subatomic-search-input", base.Input)
    add(s"$selector .subatomic-search-results", base.ResultsContainer)
    add(s"$selector .subatomic-search-result-container", base.Result)
    add(s"$selector .subatomic-search-result-container", base.Result)
    add(s"$selector .subatomic-search-result-document-url", base.DocumentUrl)
    add(
      s"$selector .subatomic-search-result-document-title",
      base.DocumentTitle
    )
    add(
      s"$selector .subatomic-search-sections-container",
      base.SectionsContainer
    )
    add(s"$selector .subatomic-search-section-url", base.SectionUrl)

    applications.result().mkString("\n")
  }

  private def renderMarkdownBase(base: MarkdownTheme) = {
    val applications = Vector.newBuilder[String]

    def add(query: String, cls: WithClassname) = {
      import ExtraStyles._
      cls match {
        case Define(name, es) =>
          es match {
            case CSS(value) =>
              applications += s".markdown $query.$name { $value }"
            case TailwindApply(classes) =>
              applications += s".markdown $query.$name { @apply $classes}"
          }

        case _ =>
          cls.className.filter(_.trim.nonEmpty).foreach { cls =>
            applications += s".markdown $query { @apply $cls}"
          }
      }
    }

    add("p", base.Paragraph)
    add("p > a", base.Link)
    add("li > a", base.Link)
    add("blockquote > a", base.Link)
    add("ul", base.UnorderedList.Container)
    add("ul > li", base.UnorderedList.Item)
    add("ol", base.OrderedList.Container)
    add("h1", base.Headings.H1)
    add("h2", base.Headings.H2)
    add("h3", base.Headings.H3)
    add("h4", base.Headings.H4)
    add("blockquote", base.Quote)
    add("pre", base.Preformatted)
    add("p > code", base.InlineCode)
    add("li > code", base.InlineCode)
    add("pre > code", base.Code)

    applications.result().mkString("\n")
  }

}

object BuilderSteps {

  case class SearchableDocument(
      title: String,
      path: os.Path
  )

  case class d2Resolver(d2: D2) {
    val diagrams = collection.concurrent.TrieMap
      .empty[String, (SitePath, D2Extension.Diagram)]

    def named(diag: D2Extension.Diagram): SitePath = {
      val path = SiteRoot / "assets" / "d2-diagrams" / (diag.name + ".svg")
      if (diagrams.contains(diag.name)) {
        if (diag.code.trim.nonEmpty)
          SubatomicError.raise(
            s"Diagram ${diag.name} has already been defined - if you want to reference it," +
              " use an empty code fence block"
          )

      } else {
        diagrams.update(diag.name, path -> diag)
      }

      path
    }

    def immediate(diag: D2Extension.Diagram): String = {
      d2.diagram(diag.code, diag.args)
    }

    def collected() = diagrams.values.toMap

  }

  // def d2Resolver(d2: D2): (
  //     () => Map[SitePath, D2Extension.Diagram],
  //     D2Extension.Diagram => SitePath
  // ) = {
  //   // val diagrams =
  //   //   collection.mutable.Map.empty[String, (SitePath, D2Extension.Diagram)]

  //   // val diagramResolver: D2Extension.Diagram => SitePath = { diag =>
  //   //   val path = SiteRoot / "assets" / "d2-diagrams" / (diag.name + ".svg")
  //   //   if (diagrams.contains(diag.name)) {
  //   //     if (diag.code.trim.nonEmpty)
  //   //       SubatomicError.raise
  //   //         s"Diagram ${diag.name} has already been defined - if you want to reference it," +
  //   //           " use an empty code fence block"
  //   //       )

  //   //   } else {
  //   //     diagrams.update(diag.name, path -> diag)
  //   //   }

  //   //   path
  //   // }

  //   // (() => diagrams.values.toMap, diagramResolver)
  // }

}
