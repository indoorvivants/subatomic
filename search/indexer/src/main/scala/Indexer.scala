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

class Indexer[ContentId, ContentType](
    content: Iterable[(ContentId, ContentType)],
    tokenizer: String => Vector[String] = DefaultTokenizer
) {
  def processSome(
      extract: PartialFunction[ContentType, String]
  ): SearchIndex[ContentId] = {
    val total  = extract.lift
    val tf_idf = new TF_IDF[ContentId](content.size)
    content.foreach {
      case (contentId, content) =>
        total(content).foreach { contentAsString =>
          val tokens = tokenizer(contentAsString)

          tf_idf.add(contentId, tokens)
        }
    }

    tf_idf.buildIndex
  }

  def processAll(
      extract: ContentType => String
  ) = processSome { case c => extract(c) }
}

object Indexer {
  def default[ContentId, ContentType](
      content: Iterable[(ContentId, ContentType)]
  ): Indexer[ContentId, ContentType] = new Indexer(content)
}

private[subatomic] class TF_IDF[ContentId](collectionSize: Int) {
  private val documentIndexes     = mutable.Map[ContentId, DocumentIdx]()
  private val globalTermFrequency = mutable.Map[TermIdx, GlobalTermFrequency]()
  private val termsMapping        = mutable.Map[TermName, TermIdx]()
  private val termsInDocuments =
    mutable.Map[TermIdx, mutable.Map[DocumentIdx, TermFrequency]]()

  def add(contentId: ContentId, tokens: Vector[String]) = {
    val docIdx = documentIndexes.getOrElseUpdate(
      contentId,
      DocumentIdx(documentIndexes.size)
    )

    tokens
      .groupBy(identity)
      .map { case (tok, occs) => tok -> occs.size }
      .foreach {
        case (tok, occurrences) =>
          val termIdx = termsMapping.getOrElseUpdate(
            TermName(tok),
            TermIdx(termsMapping.size)
          )

          globalTermFrequency.update(
            termIdx,
            globalTermFrequency.getOrElse(termIdx, GlobalTermFrequency(0)).inc
          )

          termsInDocuments.get(termIdx) match {
            case Some(value) => value.update(docIdx, TermFrequency(occurrences))
            case None =>
              termsInDocuments.update(
                termIdx,
                mutable.Map(docIdx -> TermFrequency(occurrences))
              )
          }
      }
  }

  def invertTermsToDocuments: Map[DocumentIdx, Map[TermIdx, TermFrequency]] = {
    val acc = mutable.Map[DocumentIdx, mutable.Map[TermIdx, TermFrequency]]()

    termsInDocuments.foreach {
      case (termIdx, frequenceInDocuments) =>
        frequenceInDocuments.foreach {
          case (docIdx, termFrequency) =>
            acc
              .getOrElseUpdate(docIdx, mutable.Map.empty)
              .update(termIdx, termFrequency)
        }
    }

    acc.map { case (k, v) => k -> v.toMap }.toMap
  }

  def buildIndex: SearchIndex[ContentId] = {
    SearchIndex(
      documentIndexes.map(_.swap).toMap,
      termsInDocuments.map { case (k, v) => k -> v.toMap }.toMap,
      globalTermFrequency.toMap,
      termsMapping.toMap,
      invertTermsToDocuments,
      CollectionSize(collectionSize),
      CharTree.build(termsMapping)
    )
  }
}
