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

import java.nio.file.Path

import decline_derive._

@Name("search-eval")
@Help(
  "Evaluate subatomic search on NFCorpus (Boteva et al., ECIR 2016) and report nDCG@10"
)
case class EvalArgs(
    @Positional("nfcorpus-dir")
    @Help(
      "Unpacked NFCorpus folder, containing {train,dev,test}.docs, *.queries, *.qrel and raw/doc_dump.txt"
    )
    dataset: Path,
    @Help("Split to evaluate: train, dev or test")
    split: String = "test",
    @Help(
      "Query type: titles, vid-titles, vid-desc, nontopic-titles or all"
    )
    queries: String = "titles",
    @Help(
      "Relevance grades: 2-1-0 (used in the NFCorpus paper and by BEIR) or 3-2-1"
    )
    grades: String = "2-1-0",
    @Name("per-query")
    @Help("Print nDCG@10 for every query, worst first")
    perQuery: Boolean = false
) derives CommandApplication

object SearchEval extends CommandApplication.Main[EvalArgs] {
  final val K = 10

  def run(args: EvalArgs): Unit = {
    val root = os.Path(args.dataset.toAbsolutePath())

    Dataset.load(root, args.split, args.queries, args.grades) match {
      case Left(error) =>
        System.err.println(error)
        sys.exit(1)

      case Right(dataset) =>
        val result = evaluate(dataset)

        if (args.perQuery)
          result.perQuery.sortBy(_._2).foreach { case (query, score) =>
            println(f"$score%.4f  $query")
          }

        println(s"Documents indexed: ${dataset.corpus.size}")
        println(
          s"Queries evaluated: ${result.perQuery.size} (split: ${args.split}, queries: ${args.queries}, grades: ${args.grades})"
        )
        println(f"nDCG@$K: ${result.ndcg}%.4f")
    }
  }

  case class Result(perQuery: Vector[(String, Double)]) {
    def ndcg: Double =
      if (perQuery.isEmpty) 0.0 else perQuery.map(_._2).sum / perQuery.size
  }

  def evaluate(dataset: Dataset): Result = {
    val index = Indexer
      .default(dataset.corpus)
      .processAll { doc =>
        Document(doc.title, doc.id, Vector(Section(doc.title, None, doc.text)))
      }

    val search = new Search(index)

    // Queries without any relevant documents are skipped (nDCG is undefined
    // for them); queries with no search results count as 0.
    val perQuery = for {
      (queryId, judgements) <- dataset.qrels.toVector.sortBy(_._1)
      query                 <- dataset.queries.get(queryId)
      ranking = search.string(query).entries.take(K).map(_._1.document.url)
      score <- Metrics.ndcgAt(K, ranking, judgements)
    } yield query -> score

    Result(perQuery)
  }
}
