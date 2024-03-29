package subatomic
package search

import upickle.default._
import scala.util.{Try, Success}

object JsonRoundtripTests extends verify.BasicTestSuite {
  test("SearchIndex roundtrip") {
    val content = Vector(
      "/"            -> "lorem ipsum dolor amet lorem",
      "/hello"       -> "lorem dolor",
      "/hello/world" -> "amet ipsum amet dolor",
      "/bla/bla"     -> """   "Hello quotes!" """
    )

    val idx = Indexer.default[(String, String)](content).processAll {
      case (path, text) =>
        Document.section(
          s"Document at $path",
          path,
          text
        )
    }

    def roundtrip[T: Writer: Reader](value: T) = {
      val result = Try {
        read[T](writeJs(value).render())
      }

      assert(result == Success(value))
    }

    roundtrip(idx)
  }
}
