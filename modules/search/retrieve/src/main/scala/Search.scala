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

import scala.io.StdIn

case class SearchResults(
    entries: Vector[(ResultsEntry, Double)]
)

case class ResultsEntry(
    document: DocumentEntry,
    sections: List[SectionEntry]
)

class Search(index: SearchIndex, debug: Boolean = false) {
  @inline def debugPrint(s: Any) = {
    if (debug) println(s)
  }

  def string(s: String): SearchResults = {
    val tokens = DefaultTokenizer(s)

    val terms = tokens.distinct.flatMap { tok =>
      for {
        termIdx <- index.resolveTerm(tok)
        _ = debugPrint(s"token $tok resolved to $termIdx")
        docsWithTerm <- index.termsInDocuments.get(termIdx.value)
        _ = debugPrint(s"documents with $tok: $docsWithTerm")
      } yield termIdx -> docsWithTerm
    }

    val validTerms = terms.map(_._1)
    val candidates = terms.flatMap(_._2.keys)

    val sectionRef =
      scala.collection.mutable.HashMap[(DocumentIdx, SectionIdx), Double]()

    val termDocumentRanks = candidates.flatMap { documentId =>
      val documentTerms = getDocumentTerms(documentId)

      validTerms.map { termId =>
        documentTerms.get(termId.value) match {
          case Some(tdo) =>
            val TF = Algorithms.augmented_Term_Frequency(
              termId.value,
              documentTerms.map { case (k, v) => k -> v.frequencyInDocument }
            )

            val IDF = Algorithms.inverse_Document_Frequency(
              index.collectionSize,
              getGlobalTermFrequency(termId.value)
            )

            debugPrint(
              s"DocumentId: $documentId, Term: ${termId}, TF: $TF, IDF: $IDF"
            )

            val TERM_SCORE = TF * IDF

            tdo.sectionOccurences.map { case (sectionIdx, frequencyInSection) =>
              val key = (documentId, sectionIdx)
              debugPrint(
                s"Updating ($documentId, $sectionIdx) for $termId with ${frequencyInSection.value * TERM_SCORE}"
              )
              sectionRef.update(
                key,
                frequencyInSection.value * TERM_SCORE + sectionRef
                  .getOrElseUpdate(key, 0.0)
              )
            }

            (documentId, TERM_SCORE)
          case None => (documentId, 0.0)
        }
      }
    }

    val entries = termDocumentRanks
      .groupBy(_._1)
      .map { case (documentIdx, ranks) =>
        val document = index.documentsMapping(documentIdx)
        val documentSections =
          document.sections.keys.toVector
            .map(sid => sid -> sectionRef.getOrElse((documentIdx, sid), 0.0))
            .filter(_._2 != 0.0)
            .sortBy(-1 * _._2)
            .take(3)
            .map(_._1)

        debugPrint(
          s"document: ${document.title}, sections: ${documentSections}"
        )

        debugPrint(s"document: ${document.title}, Section ref: $sectionRef")

        val resultEntry = ResultsEntry(
          document,
          documentSections.map(document.sections).toList
        )

        resultEntry -> ranks.map(_._2).sum
      }
      .toVector
      .sortBy(-1 * _._2)
      .filter(_._1.sections.nonEmpty)

    SearchResults(entries)
  }

  def getDocumentSections(documentId: DocumentIdx): List[SectionIdx] =
    index.sectionMapping(documentId)

  def getDocumentTerms(
      documentId: DocumentIdx
  ): Map[TermIdx, TermDocumentOccurence] =
    index.documentTerms(documentId)

  def getGlobalTermFrequency(termId: TermIdx): GlobalTermFrequency =
    index.globalTermFrequency(termId)

}

object Algorithms {
  def augmented_Term_Frequency(
      term: TermIdx,
      document: Map[TermIdx, TermFrequency]
  ) = {
    if (document.isEmpty || !document.contains(term)) 0.0
    else {
      val maxFreq = document.maxBy(_._2.value.toDouble)._2.value.toDouble
      val freq    = document(term).value.toDouble

      0.5 + 0.5 * (freq / maxFreq)
    }
  }

  def inverse_Document_Frequency(
      numDocuments: CollectionSize,
      globalTermFrequency: GlobalTermFrequency
  ) = {
    if (globalTermFrequency.value == numDocuments.value)
      math.log(
        numDocuments.value.toDouble / (numDocuments.value.toDouble + 1.0)
      )
    else
      math.log(numDocuments.value.toDouble / globalTermFrequency.value.toDouble)
  }
}

object Search {
  def query(idx: SearchIndex, q: String, debug: Boolean) = {
    val search  = new Search(idx, debug)
    val results = search.string(q)
    renderResults(results)
  }
  def cli(idx: SearchIndex, debug: Boolean) = {
    val search = new Search(idx, debug)

    var cmd = ""

    println("Type :q to exit")

    while (cmd != ":q") {
      print("query > ")
      cmd = StdIn.readLine().trim()

      if (cmd != ":q") {
        val results = search.string(cmd)
        renderResults(results)
      }
    }
  }

  private def renderResults(
      res: SearchResults
  ) = {
    if (res.entries.isEmpty) println("NO RESULTS")
    else
      res.entries.foreach { case (ResultsEntry(document, sections), score) =>
        println(
          Console.BOLD + score.toString() + Console.RESET + " " + document.title
        )

        sections.foreach { case SectionEntry(title, _) =>
          println("   - " + title)
          println()
        }
      }
  }
}
