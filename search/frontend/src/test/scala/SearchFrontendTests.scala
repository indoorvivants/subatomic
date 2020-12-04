package subatomic
package search

import upickle.default._
import utest._

import SearchIndex._

object SearchFrontendTests extends TestSuite {
  val tests = Tests {

    test("SearchIndex[String] roundtrip") {

      val content = Vector(
        "/"            -> "lorem ipsum dolor amet lorem",
        "/hello"       -> "lorem dolor",
        "/hello/world" -> "amet ipsum amet dolor"
      )

      val idx = Indexer.default[String, String](content).processAll(identity)

      val o = read[SearchIndex[String]](writeJs(idx).render())

      assert(o == idx)
    }

  }
}
