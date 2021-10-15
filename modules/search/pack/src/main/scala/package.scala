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

package object search {

  // class SearchIndexProcessor[Content](f: PartialFunction[Content, Document]) extends Processor[Content, SiteAsset]

  // implicit class SiteSearchOpts[Content](site: Site[Content]) {
  //     def addSearchIndex(jsPath: SitePath)(f: (SitePath, Content) => Document)

  // }
  // val rawLinker = new Linker(rawContent, siteRoot)

  // val jsonIndex = search.Indexer
  //   .default(rawContent)
  //   .processSome {
  //     case (sitePath, Doc(title, mdPath, _)) =>
  //       Document.section(
  //         title,
  //         rawLinker.resolve(_ / sitePath),
  //         os.read(mdPath)
  //       )
  //   }
  //   .asJsonString

  // val lines = jsonIndex.grouped(100).map(_.replaceAllLiterally("'", "\\'")).map(str => s"'${str}'").mkString(",\n")

  // val tmpFile = os.temp {
  //   s"""
  //   var ln = [$lines];var SearchIndexText = ln.join('')
  //   """
  // }

  // val searchIndexContent =
  //   SiteRoot / "assets" / "search-index.js" -> StaticFile(tmpFile)

  // val tmpFileJS = os.temp(search.SearchFrontendPack.fullJS)

  // val searchJSContent =
  //   SiteRoot / "assets" / "search.js" -> StaticFile(tmpFileJS)
}
