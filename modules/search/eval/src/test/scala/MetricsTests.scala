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

object MetricsTests extends weaver.FunSuite {
  def log2(x: Double) = math.log(x) / math.log(2)

  test("perfect ranking scores 1") {
    val judgements = Map("a" -> 2, "b" -> 1)
    expect(Metrics.ndcgAt(10, Vector("a", "b", "x"), judgements) == Some(1.0))
  }

  test("ranking with nothing relevant scores 0") {
    expect(Metrics.ndcgAt(10, Vector("x", "y"), Map("a" -> 1)) == Some(0.0))
  }

  test("undefined when nothing is relevant") {
    expect(Metrics.ndcgAt(10, Vector("a"), Map("a" -> 0)) == None)
  }

  test("uses linear gains and log2 discounts") {
    val judgements = Map("a" -> 2, "b" -> 1)
    val actual     = Metrics.ndcgAt(10, Vector("b", "a"), judgements).get
    val expected   = (1 + 2 / log2(3)) / (2 + 1 / log2(3))

    expect(math.abs(actual - expected) < 1e-12)
  }

  test("only the top k results count") {
    val judgements = Map("a" -> 1)
    expect(Metrics.ndcgAt(1, Vector("x", "a"), judgements) == Some(0.0))
  }
}
