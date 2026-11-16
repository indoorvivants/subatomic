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
package eval

/** NFCorpus, as distributed by its authors
  * (https://www.cl.uni-heidelberg.de/statnlpgroup/nfcorpus/):
  *
  * {{{
  * {train,dev,test}.docs                 ID <TAB> preprocessed text
  * {train,dev,test}.<type>.queries       ID <TAB> preprocessed text
  * {train,dev,test}.{2-1-0,3-2-1}.qrel   QUERY_ID <TAB> 0 <TAB> DOC_ID <TAB> GRADE
  * raw/doc_dump.txt                      ID <TAB> URL <TAB> TITLE <TAB> ABSTRACT
  * }}}
  *
  * The corpus is the union of documents of all three splits (3633 documents,
  * the same corpus BEIR uses). Their text is taken from `raw/doc_dump.txt`,
  * which is not lowercased, stopword-filtered or stripped of punctuation, and
  * keeps the title separate - so our own tokenizer and title weighting are
  * what's being evaluated.
  */
case class Dataset(
    corpus: Vector[Dataset.Doc],
    queries: Map[String, String],
    qrels: Map[String, Map[String, Int]]
)

object Dataset {
  case class Doc(id: String, title: String, text: String)

  val Splits     = List("train", "dev", "test")
  val QueryTypes =
    List("titles", "vid-titles", "vid-desc", "nontopic-titles", "all")
  val Grades = List("2-1-0", "3-2-1")

  def load(
      root: os.Path,
      split: String,
      queryType: String,
      grades: String
  ): Either[String, Dataset] = {
    def oneOf(name: String, value: String, allowed: List[String]) =
      Either.cond(
        allowed.contains(value),
        (),
        s"Unknown $name '$value', expected one of: ${allowed.mkString(", ")}"
      )

    val docsFiles   = Splits.map(s => root / s"$s.docs")
    val rawDocsFile = root / "raw" / "doc_dump.txt"
    val queriesFile = root / s"$split.$queryType.queries"
    val qrelsFile   = root / s"$split.$grades.qrel"

    for {
      _ <- oneOf("split", split, Splits)
      _ <- oneOf("query type", queryType, QueryTypes)
      _ <- oneOf("grades", grades, Grades)
      missing = (rawDocsFile :: queriesFile :: qrelsFile :: docsFiles)
        .filterNot(os.isFile)
      _ <- Either.cond(
        missing.isEmpty,
        (),
        s"Not an NFCorpus folder, missing: ${missing.mkString(", ")}"
      )
      corpusIds = docsFiles.flatMap(f => tsv(f).map(_.head)).distinct
      raw       = tsv(rawDocsFile).collect { case Array(id, _, title, text) =>
        id -> Doc(id, cleanTitle(title), text)
      }.toMap
      corpus <- corpusIds.partition(raw.contains) match {
        case (found, Nil)  => Right(found.map(raw).toVector)
        case (_, notFound) =>
          Left(
            s"${notFound.size} documents are missing from $rawDocsFile, e.g. ${notFound.take(5).mkString(", ")}"
          )
      }
    } yield Dataset(
      corpus = corpus,
      queries = tsv(queriesFile).collect { case Array(id, text) =>
        id -> text
      }.toMap,
      qrels = tsv(qrelsFile)
        .collect {
          case Array(query, _, doc, grade) if grade.toIntOption.isDefined =>
            (query, doc, grade.toInt)
        }
        .groupMap(_._1)(r => r._2 -> r._3)
        .view
        .mapValues(_.toMap)
        .toMap
    )
  }

  private def tsv(file: os.Path): Vector[Array[String]] =
    os.read
      .lines(file)
      .iterator
      .filter(_.trim.nonEmpty)
      .map(_.split('\t').map(_.trim))
      .toVector

  // PubMed page titles end with " - PubMed - NCBI"
  private def cleanTitle(title: String) =
    title.stripSuffix(" - PubMed - NCBI").trim
}
