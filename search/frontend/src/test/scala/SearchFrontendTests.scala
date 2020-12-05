package subatomic
package search

import upickle.default._
import weaver.PureIOSuite
import weaver.SimpleMutableIOSuite

import SearchIndex._

object SearchFrontendTests extends SimpleMutableIOSuite {
  pureTest("SearchIndex[String] roundtrip") {

    val content = Vector(
      "/"            -> "lorem ipsum dolor amet lorem",
      "/hello"       -> "lorem dolor",
      "/hello/world" -> "amet ipsum amet dolor"
    )

    val idx = Indexer.default[String, String](content).processAll(identity)

    val o = read[SearchIndex[String]](writeJs(idx).render())

    expect(o == idx)
  }
}
