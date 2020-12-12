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

trait Primitive[T]

case class TermIdx(value: Int)       extends Primitive[Int]
case class TermName(value: String)   extends Primitive[String]
case class SectionIdx(value: Int)    extends Primitive[Int]
case class DocumentIdx(value: Int)   extends Primitive[Int]
case class TermFrequency(value: Int) extends Primitive[Int]
case class GlobalTermFrequency(value: Int) extends Primitive[Int] {
  def inc            = copy(value + 1)
  def add(more: Int) = copy(value + more)
}
case class CollectionSize(value: Int) extends Primitive[Int]

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

/**
  * There she is. The wholy grail. What we want to achieve in the end.
  *
  * @param documentsMapping
  * @param termsInDocuments
  * @param globalTermFrequency
  * @param termMapping
  * @param collectionSize
  */
case class SearchIndex private (
    documentsMapping: Map[DocumentIdx, DocumentEntry],
    termsInDocuments: Map[TermIdx, Map[DocumentIdx, TermDocumentOccurence]],
    globalTermFrequency: Map[TermIdx, GlobalTermFrequency],
    termMapping: Map[TermName, TermIdx],
    documentTerms: Map[DocumentIdx, Map[TermIdx, TermDocumentOccurence]],
    collectionSize: CollectionSize,
    charTree: CharTree
) {

  case class Found[T] private (value: T)

  def resolveTerm(s: String): Option[Found[TermIdx]] =
    termMapping.get(TermName(s)).map(Found(_))

  def asJson: ujson.Value = {
    import upickle.default._

    writeJs(this)
  }

  def asJsonString = asJson.render()
}

import upickle.default._

object SearchIndex {
  implicit val termIdxReader  = IntReader.map(TermIdx)
  implicit val termNameReader = StringReader.map(TermName)
  implicit val docIdxReader   = IntReader.map(DocumentIdx)
  implicit val tfReader       = IntReader.map(TermFrequency)
  implicit val gtfReader      = IntReader.map(GlobalTermFrequency)
  implicit val csReader       = IntReader.map(CollectionSize)
  implicit val sectIdxR       = IntReader.map(SectionIdx)

  implicit val termIdxWriter: Writer[TermIdx]    = IntWriter.comap(_.value)
  implicit val termNameWriter: Writer[TermName]  = StringWriter.comap(_.value)
  implicit val docIdxWriter: Writer[DocumentIdx] = IntWriter.comap(_.value)
  implicit val tfWriter: Writer[TermFrequency]   = IntWriter.comap(_.value)
  implicit val sectIdxW: Writer[SectionIdx]      = IntWriter.comap(_.value)

  implicit val gtfWriter: Writer[GlobalTermFrequency] =
    IntWriter.comap(_.value)
  implicit val csWriter: Writer[CollectionSize] =
    IntWriter.comap(_.value)

  implicit val wct: Writer[CharTree] = macroW[CharTree]
  implicit val rct: Reader[CharTree] = macroR[CharTree]

  implicit val tdo = macroRW[TermDocumentOccurence]
  implicit val sec = macroRW[SectionEntry]
  implicit val doc = macroRW[DocumentEntry]

  implicit val w: Writer[SearchIndex] = macroW[SearchIndex]
  implicit val r: Reader[SearchIndex] = macroR[SearchIndex]
}
