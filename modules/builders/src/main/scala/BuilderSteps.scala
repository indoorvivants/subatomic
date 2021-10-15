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

  def buildSearchIndex[Doc](linker: Linker, d: PartialFunction[Doc, SearchableDocument])(
      content: Vector[(SitePath, Doc)]
  ): subatomic.search.SearchIndex = {
    subatomic.search.Indexer.default(content).processSome {
      case (_, raw) if d.isDefinedAt(raw) =>
        val doc = d(raw)
        val markdownSections =
          markdown.extractMarkdownSections(doc.title, linker.find(raw), doc.path)
        subatomic.search.Document(
          doc.title,
          linker.find(raw),
          markdownSections.map { case Markdown.Section(title, url, text) =>
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

    val indexJson = buildSearchIndex(linker, d)(content).asJsonString

    val lines = indexJson.grouped(500).map(_.replace("'", "\\'")).map(str => s"'${str}'").mkString(",\n")

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
        .addPage(SiteRoot / "assets" / "subatomic-search.css", BuilderTemplate.searchCSS)

  }

}

object BuilderSteps {

  case class SearchableDocument(
      title: String,
      path: os.Path
  )
}
