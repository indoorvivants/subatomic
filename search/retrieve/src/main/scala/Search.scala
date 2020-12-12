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

class Search(index: SearchIndex) {
  def string(s: String): Vector[(DocumentEntry, Double)] = {
    val tokens = DefaultTokenizer(s)

    val terms = tokens.flatMap { tok =>
      for {
        termIdx      <- index.resolveTerm(tok)
        docsWithTerm <- index.termsInDocuments.get(termIdx.value)
      } yield termIdx.value -> docsWithTerm
    }

    val validTerms = terms.map(_._1)
    val candidates = terms.flatMap(_._2.keys)

    val termDocumentRanks = candidates.flatMap { documentId =>
      val documentTerms = index.documentTerms(documentId)

      validTerms.map { termId =>
        if (documentTerms.contains(termId)) {
          val TF = Algorithms.augmented_Term_Frequency(
            termId,
            documentTerms.map { case (k, v) => k -> v.frequencyInDocument }
          )

          val IDF = Algorithms.inverse_Document_Frequency(
            index.collectionSize,
            index.globalTermFrequency(termId)
          )

          (documentId, TF * IDF)
        } else (documentId, 0.0)
      }
    }

    termDocumentRanks
      .groupBy(_._1)
      .map {
        case (documentId, ranks) =>
          index.documentsMapping(documentId) -> ranks.map(_._2).sum
      }
      .toVector
      .sortBy(-1 * _._2)
  }
}

object Algorithms {
  def augmented_Term_Frequency(
      term: TermIdx,
      document: Map[TermIdx, TermFrequency]
  ) = {
    val maxFreq = document.maxBy(_._2.value.toDouble)._2.value.toDouble
    val freq    = document(term).value.toDouble

    0.5 + 0.5 * (freq / maxFreq)
  }

  def inverse_Document_Frequency(
      numDocuments: CollectionSize,
      globalTermFrequency: GlobalTermFrequency
  ) = {
    math.log(numDocuments.value.toDouble / globalTermFrequency.value.toDouble)
  }
}
