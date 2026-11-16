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

  // Sections are the unit of retrieval: BM25 statistics are computed over them
  private val totalSections =
    index.sectionLengths.valuesIterator.map(_.size).sum

  private val averageSectionLength = {
    val total = index.sectionLengths.valuesIterator.flatten.sum
    if (totalSections == 0 || total == 0) 1.0
    else total.toDouble / totalSections
  }

  private def sectionLength(doc: DocumentIdx, section: SectionIdx): Int =
    index.sectionLengths
      .get(doc)
      .flatMap(_.lift(section.value))
      .getOrElse(averageSectionLength.toInt)

  def string(s: String): SearchResults = {
    val queryTerms = DefaultTokenizer(s).distinct

    if (queryTerms.isEmpty) return SearchResults(Vector.empty)

    val postings = queryTerms.flatMap { tok =>
      for {
        termIdx <- index.resolveTerm(tok)
        _ = debugPrint(s"token $tok resolved to $termIdx")
        docsWithTerm <- index.termsInDocuments.get(termIdx.value)
      } yield docsWithTerm
    }

    case class Acc(score: Double, matchedTerms: Int)

    val sectionScores =
      scala.collection.mutable.HashMap[(DocumentIdx, SectionIdx), Acc]()
    val documentMatches = scala.collection.mutable.HashMap[DocumentIdx, Int]()

    postings.foreach { docsWithTerm =>
      val sectionsWithTerm =
        docsWithTerm.valuesIterator.map(_.sectionOccurences.size).sum

      val idf = Algorithms.bm25_IDF(totalSections, sectionsWithTerm)

      docsWithTerm.foreach { case (documentIdx, tdo) =>
        documentMatches.update(
          documentIdx,
          documentMatches.getOrElse(documentIdx, 0) + 1
        )

        tdo.sectionOccurences.foreach { case (sectionIdx, frequency) =>
          val tf = Algorithms.bm25_TF(
            frequency.value,
            sectionLength(documentIdx, sectionIdx),
            averageSectionLength
          )

          debugPrint(
            s"($documentIdx, $sectionIdx): freq=${frequency.value}, TF=$tf, IDF=$idf"
          )

          val key  = (documentIdx, sectionIdx)
          val prev = sectionScores.getOrElse(key, Acc(0.0, 0))
          sectionScores.update(
            key,
            Acc(prev.score + tf * idf, prev.matchedTerms + 1)
          )
        }
      }
    }

    val totalTerms = queryTerms.size.toDouble

    val entries = sectionScores.toVector
      .groupBy(_._1._1)
      .map { case (documentIdx, sections) =>
        val document = index.documentsMapping(documentIdx)

        // sections are ranked by their own score, scaled by how much of the
        // query they cover
        val rankedSections = sections
          .map { case ((_, sectionIdx), acc) =>
            sectionIdx -> acc.score * (acc.matchedTerms / totalTerms)
          }
          .sortBy { case (sid, score) => (-score, sid.value) }

        // the document is as good as its best section, scaled by how much of
        // the query the document as a whole covers
        val documentScore =
          rankedSections.head._2 * (documentMatches(documentIdx) / totalTerms)

        debugPrint(
          s"document: ${document.title}, score: $documentScore, sections: $rankedSections"
        )

        ResultsEntry(
          document,
          rankedSections.take(3).map(r => document.sections(r._1)).toList
        ) -> documentScore
      }
      .toVector
      .sortBy { case (entry, score) => (-score, entry.document.url) }

    SearchResults(entries)
  }
}

object Algorithms {

  /** BM25 term frequency saturation parameter */
  final val K1 = 1.2

  /** BM25 length normalisation parameter */
  final val B = 0.75

  /** Okapi BM25 IDF, with +1 inside the log so it's always positive.
    *
    * @param collectionSize
    *   total number of retrievable units (sections)
    * @param unitsWithTerm
    *   number of units containing the term
    */
  def bm25_IDF(collectionSize: Int, unitsWithTerm: Int): Double =
    math.log(
      1.0 + (collectionSize - unitsWithTerm + 0.5) / (unitsWithTerm + 0.5)
    )

  /** BM25 term frequency component: saturates as frequency grows, and penalises
    * units longer than average.
    */
  def bm25_TF(frequency: Int, length: Int, averageLength: Double): Double =
    if (frequency <= 0) 0.0
    else {
      val norm = 1.0 - B + B * (length.toDouble / averageLength)
      frequency * (K1 + 1.0) / (frequency + K1 * norm)
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
