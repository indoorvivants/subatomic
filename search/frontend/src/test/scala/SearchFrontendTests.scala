package subatomic
package search

import upickle.default._
import weaver.PureIOSuite
import weaver.SimpleMutableIOSuite

import SearchIndex._

object SearchFrontendTests extends SimpleMutableIOSuite {
  pureTest("SearchIndex roundtrip") {

    val content = Vector(
      "/"            -> "lorem ipsum dolor amet lorem",
      "/hello"       -> "lorem dolor",
      "/hello/world" -> "amet ipsum amet dolor"
    )

    val idx = Indexer.default[(String, String)](content).processAll { case (path, text) =>
      Document.section(
        s"Document at $path",
        path,
        text
      )
    }

    val o = read[SearchIndex](writeJs(idx).render())

    expect(o == idx)
  }
}
