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

import scala.collection.mutable

case class Document(
    title: String,
    url: String,
    sections: Vector[Section]
)

case class Section(
    title: String,
    url: Option[String],
    content: String
) {
  override def toString() = {
    val cont = content.replace("\n", Console.BOLD + "\\n" + Console.RESET)

    s"Section($title, $url, $cont)"
  }
}

object Document {
  def section(title: String, url: String, content: String) = {
    Document(
      title,
      url,
      Vector(
        Section(title, Some(url), content)
      )
    )
  }
}

class Indexer[ContentType](
    content: Iterable[ContentType],
    tokenizer: String => Vector[String] = DefaultTokenizer,
    debug: Boolean = false
) {
  def processSome(
      extract: PartialFunction[ContentType, Document]
  ): SearchIndex = {
    val total  = extract.lift
    val tf_idf = new TF_IDF(content.size, tokenizer, debug)
    content.foreach {
      case cnt =>
        total(cnt).foreach { tf_idf.add }
    }

    tf_idf.buildIndex
  }

  def processAll(
      extract: ContentType => Document
  ) = processSome { case c => extract(c) }
}

object Indexer {
  def default[ContentType](
      content: Iterable[ContentType],
      debug: Boolean = false
  ): Indexer[ContentType] = new Indexer(content, debug = debug)
}

private[subatomic] class TF_IDF(
    collectionSize: Int,
    tokenizer: String => Vector[String],
    debug: Boolean = false
) {

  @inline def debugPrint(s: Any) = {
    if (debug) println(s)
  }

  private val documentIndexes     = mutable.Map[Document, DocumentIdx]()
  private val globalTermFrequency = mutable.Map[TermIdx, GlobalTermFrequency]()
  private val termsMapping        = mutable.Map[TermName, TermIdx]()
  private val termsInDocuments =
    mutable
      .Map[TermIdx, mutable.Map[DocumentIdx, TermDocumentOccurence]]()

  private val documentEntries = mutable.Map[DocumentIdx, DocumentEntry]()
  private val sectionEntries  = mutable.Map[DocumentIdx, List[SectionIdx]]()

  private val ZeroTermFreq = TermFrequency(0)

  private def getDocumentId(document: Document) = {
    documentIndexes.getOrElseUpdate(
      document,
      DocumentIdx(documentIndexes.size)
    )
  }

  private def addDocumentEntry(docIdx: DocumentIdx, entry: DocumentEntry) = {
    documentEntries.update(docIdx, entry)
  }

  def increaseGlobalTermFrequency(termIdx: TermIdx, by: TermFrequency) = {
    val current = globalTermFrequency
      .getOrElse(termIdx, GlobalTermFrequency(0))

    globalTermFrequency.update(termIdx, current.add(by.value))
  }

  def getTermId(token: String) = {
    termsMapping.getOrElseUpdate(
      TermName(token),
      TermIdx(termsMapping.size)
    )
  }

  def getTermDocumentOccurennce(
      termId: TermIdx,
      docId: DocumentIdx
  ): TermDocumentOccurence = {

    termsInDocuments.get(termId) match {
      case Some(value) =>
        value.getOrElseUpdate(
          docId,
          TermDocumentOccurence(ZeroTermFreq, Map.empty)
        )
      case None =>
        val zerofreq = TermDocumentOccurence(ZeroTermFreq, Map.empty)
        val base     = mutable.Map(docId -> zerofreq)

        termsInDocuments.update(termId, base)

        zerofreq
    }

  }

  def updateTermDocumentOccurrence(
      termId: TermIdx,
      docIdx: DocumentIdx,
      tdo: TermDocumentOccurence
  ) = {
    termsInDocuments(termId).update(docIdx, tdo)
  }

  def add(document: Document) = {
    val docIdx = getDocumentId(document)

    val documentEntry = DocumentEntry(
      document.title,
      document.url,
      document.sections.zipWithIndex.map {
        case (section, idx) =>
          SectionIdx(idx) -> SectionEntry(
            title = section.title,
            url = section.url.getOrElse(document.url)
          )
      }.toMap
    )

    addDocumentEntry(docIdx, documentEntry)

    debugPrint(s"----\nIndexing $docIdx $document\n----")

    document.sections.zipWithIndex
      .map { case (s, i) => s -> SectionIdx(i) }
      .foreach {
        case (Section(_, _, text), sectionIdx) =>
          val tokens             = tokenizer(text)
          val documentRegistered = mutable.Set[TermIdx]()

          tokens
            .groupBy(identity)
            .map { case (tok, occs) => tok -> TermFrequency(occs.size) }
            .foreach {
              case (tok, termFrequency) =>
                val termIdx = getTermId(tok)

                if (!documentRegistered.contains(termIdx)) {
                  increaseGlobalTermFrequency(termIdx, TermFrequency(1))
                  documentRegistered.add(termIdx)
                }

                val currentStats = getTermDocumentOccurennce(termIdx, docIdx)

                val newStats = currentStats.copy(
                  frequencyInDocument = currentStats.frequencyInDocument + termFrequency,
                  sectionOccurences = currentStats.sectionOccurences
                    .updated(sectionIdx, termFrequency)
                )

                updateTermDocumentOccurrence(termIdx, docIdx, newStats)
            }
      }
  }

  implicit class TermFrequencyOpts(tf: TermFrequency) {
    def +(other: TermFrequency) = TermFrequency(tf.value + other.value)
  }

  lazy val invertTermsToDocuments: Map[DocumentIdx, Map[TermIdx, TermDocumentOccurence]] = {
    val acc =
      mutable.Map[DocumentIdx, mutable.Map[TermIdx, TermDocumentOccurence]]()

    termsInDocuments.foreach {
      case (termIdx, frequenceInDocuments) =>
        frequenceInDocuments.foreach {
          case (docIdx, termFrequency) =>
            acc.get(docIdx) match {
              case Some(value) =>
                value.update(termIdx, termFrequency)
              case None =>
                acc.update(docIdx, mutable.Map(termIdx -> termFrequency))
            }
        }
    }

    acc.map { case (k, v) => k -> v.toMap }.toMap
  }

  def buildIndex: SearchIndex = {
    SearchIndex(
      documentEntries.toMap,
      termsInDocuments.map { case (k, v) => k -> v.toMap }.toMap,
      globalTermFrequency.toMap,
      termsMapping.toMap,
      invertTermsToDocuments,
      sectionEntries.toMap,
      CollectionSize(collectionSize),
      CharTree.build(termsMapping)
    )
  }
}
