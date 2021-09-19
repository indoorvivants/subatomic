package subatomic
package search

import upickle.default._
import scala.util.{Try, Success}
import subatomic.search.Document
import subatomic.search.Indexer
class JsonRoundtripTests extends munit.FunSuite {
  test("SearchIndex roundtrip") {
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

    def roundtrip[T: Writer: Reader](value: T) = {
      val result = Try {
        read[T](writeJs(value).render())
      }

      assertEquals(result, Success(value))

      // expect.same(result, Success(value))
    }

    import SearchIndex._
    // val entry = SectionEntry("yo", "what")
    // roundtrip(entry)

    roundtrip(idx.charTree)
  }
}
