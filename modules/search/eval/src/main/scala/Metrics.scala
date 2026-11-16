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

object Metrics {

  /** Normalised Discounted Cumulative Gain at rank k, with linear gains (`gain =
    * relevance grade`), matching trec_eval's `ndcg_cut` which BEIR reports.
    *
    * @param ranking
    *   document ids, best first
    * @param judgements
    *   relevance grade for each judged document, unjudged ones count as 0
    * @return
    *   None if the query has no relevant documents (nDCG is undefined)
    */
  def ndcgAt(
      k: Int,
      ranking: Seq[String],
      judgements: Map[String, Int]
  ): Option[Double] = {
    val ideal = dcg(judgements.values.filter(_ > 0).toVector.sortBy(-_).take(k))

    Option.when(ideal > 0) {
      dcg(ranking.take(k).map(judgements.getOrElse(_, 0))) / ideal
    }
  }

  private def dcg(gains: Seq[Int]): Double =
    gains.iterator.zipWithIndex.map { case (gain, i) =>
      gain / (math.log(i + 2.0) / math.log(2.0))
    }.sum
}
