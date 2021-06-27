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
    sectionMapping: Map[DocumentIdx, List[SectionIdx]],
    collectionSize: CollectionSize,
    charTree: CharTree
) {

  def resolveTerm(s: String): Option[TermIdx] =
    termMapping.get(TermName(s))

  def asJson: ujson.Value = {
    import upickle.default._

    writeJs(this)
  }

  def asJsonString = asJson.render()
}

import upickle.default._

object SearchIndex {
  implicit val termIdxReader: Reader[TermIdx]         = IntReader.map(TermIdx.apply)
  implicit val termNameReader: Reader[TermName]       = StringReader.map(TermName.apply)
  implicit val docIdxReader: Reader[DocumentIdx]      = IntReader.map(DocumentIdx.apply)
  implicit val tfReader: Reader[TermFrequency]        = IntReader.map(TermFrequency.apply)
  implicit val gtfReader: Reader[GlobalTermFrequency] = IntReader.map(GlobalTermFrequency.apply)
  implicit val csReader: Reader[CollectionSize]       = IntReader.map(CollectionSize.apply)
  implicit val sectIdxR: Reader[SectionIdx]           = IntReader.map(SectionIdx.apply)

  implicit val termIdxWriter: Writer[TermIdx]    = IntWriter.comap(_.value)
  implicit val termNameWriter: Writer[TermName]  = StringWriter.comap(_.value)
  implicit val docIdxWriter: Writer[DocumentIdx] = IntWriter.comap(_.value)
  implicit val tfWriter: Writer[TermFrequency]   = IntWriter.comap(_.value)
  implicit val sectIdxW: Writer[SectionIdx]      = IntWriter.comap(_.value)

  implicit val gtfWriter: Writer[GlobalTermFrequency] =
    IntWriter.comap(_.value)
  implicit val csWriter: Writer[CollectionSize] =
    IntWriter.comap(_.value)

  implicit def wct: Writer[CharTree] =
    macroW[(Map[Char, CharTree], Option[TermIdx])].comap[CharTree](ct => (ct.data, ct.terminal))

  implicit def rct: Reader[CharTree] = macroR[(Map[Char, CharTree], Option[TermIdx])].map(c => CharTree(c._1, c._2))

  implicit val tdoR: Reader[TermDocumentOccurence] =
    macroR[(TermFrequency, Map[SectionIdx, TermFrequency])].map(i => TermDocumentOccurence(i._1, i._2))

  implicit val tdoW: Writer[TermDocumentOccurence] =
    macroW[(TermFrequency, Map[SectionIdx, TermFrequency])].comap[TermDocumentOccurence](t =>
      (t.frequencyInDocument, t.sectionOccurences)
    )
  implicit val secR: Reader[SectionEntry] = macroRW[(String, String)].map(s => SectionEntry(s._1, s._2))
  implicit val secW: Writer[SectionEntry] = macroW[(String, String)].comap[SectionEntry](s => (s.title, s.url))

  implicit val docR: Reader[DocumentEntry] =
    macroR[(String, String, Map[SectionIdx, SectionEntry])].map(s => DocumentEntry(s._1, s._2, s._3))
  implicit val docW: Writer[DocumentEntry] =
    macroW[(String, String, Map[SectionIdx, SectionEntry])].comap[DocumentEntry](d => (d.title, d.url, d.sections))

  implicit val w: Writer[SearchIndex] = macroW[SearchIndex]
  implicit val r: Reader[SearchIndex] = macroR[SearchIndex]
}
