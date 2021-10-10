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
package search

case class TermIdx(value: Int)
case class TermName(value: String)
case class SectionIdx(value: Int)
case class DocumentIdx(value: Int)
case class TermFrequency(value: Int)
case class GlobalTermFrequency(value: Int) {
  def inc            = copy(value + 1)
  def add(more: Int) = copy(value + more)
}
case class CollectionSize(value: Int)

case class TermDocumentOccurence(
    frequencyInDocument: TermFrequency,
    sectionOccurences: Map[SectionIdx, TermFrequency]
)

case class DocumentEntry(
    title: String,
    url: String,
    sections: Map[SectionIdx, SectionEntry]
)

case class SectionEntry(
    title: String,
    url: String
)

/** There she is. The wholy grail. What we want to achieve in the end.
  *
  * @param documentsMapping
  * @param termsInDocuments
  * @param globalTermFrequency
  * @param termMapping
  * @param collectionSize
  */
case class SearchIndex private[search] (
    documentsMapping: Map[DocumentIdx, DocumentEntry],
    termsInDocuments: Map[TermIdx, Map[DocumentIdx, TermDocumentOccurence]],
    globalTermFrequency: Map[TermIdx, GlobalTermFrequency],
    termMapping: Map[TermName, TermIdx],
    documentTerms: Map[DocumentIdx, Map[TermIdx, TermDocumentOccurence]],
    sectionMapping: Map[DocumentIdx, List[SectionIdx]],
    collectionSize: CollectionSize,
    charTree: CharTree
) {

  case class Found private[search] (value: TermIdx)

  def resolveTerm(s: String): Option[Found] =
    termMapping.get(TermName(s)).map(Found(_))

  def asJson: ujson.Value = {
    import upickle.default._

    writeJs(this)
  }

  def asJsonString = asJson.render()
}

import upickle.default._

object SearchIndex {
  implicit val termIdxReader: Reader[TermIdx]         = IntReader.map(TermIdx(_))
  implicit val termNameReader: Reader[TermName]       = StringReader.map(TermName(_))
  implicit val docIdxReader: Reader[DocumentIdx]      = IntReader.map(DocumentIdx(_))
  implicit val tfReader: Reader[TermFrequency]        = IntReader.map(TermFrequency(_))
  implicit val gtfReader: Reader[GlobalTermFrequency] = IntReader.map(GlobalTermFrequency(_))
  implicit val csReader: Reader[CollectionSize]       = IntReader.map(CollectionSize(_))
  implicit val sectIdxR: Reader[SectionIdx]           = IntReader.map(SectionIdx(_))

  implicit val termIdxWriter: Writer[TermIdx]    = IntWriter.comap(_.value)
  implicit val termNameWriter: Writer[TermName]  = StringWriter.comap(_.value)
  implicit val docIdxWriter: Writer[DocumentIdx] = IntWriter.comap(_.value)
  implicit val tfWriter: Writer[TermFrequency]   = IntWriter.comap(_.value)
  implicit val sectIdxW: Writer[SectionIdx]      = IntWriter.comap(_.value)

  implicit val gtfWriter: Writer[GlobalTermFrequency] =
    IntWriter.comap(_.value)

  implicit val csWriter: Writer[CollectionSize] =
    IntWriter.comap(_.value)

  implicit val wct: ReadWriter[CharTree] = {
    def write(c: CharTree): ujson.Value = {
      val termId = writeJs(c.terminal)

      ujson.Arr(
        ujson.Obj.from(c.data.toSeq.map { case (char, tree) =>
          char.toString -> write(tree)
        }),
        termId
      )
    }

    def read(c: ujson.Value): CharTree = {
      val arr = c.arr

      val termId = upickle.default.read[Option[TermIdx]](arr.apply(1))
      val rest = arr.apply(0).obj.map { case (key, value) =>
        key.charAt(0) -> read(value)
      }

      CharTree(rest.toMap, termId)

    }
    readwriter[ujson.Value].bimap[CharTree](write, read)
  }

  implicit val tdoR: ReadWriter[TermDocumentOccurence] =
    macroRW

  implicit val secR: ReadWriter[SectionEntry] = macroRW
  implicit val doc: ReadWriter[DocumentEntry] = macroRW

  implicit val w: ReadWriter[SearchIndex] = macroRW[SearchIndex]
}
