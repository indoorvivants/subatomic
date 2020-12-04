package subatomic
package search

import utest._

object SearchTests extends TestSuite {
  val tests = Tests {

    test("basic") {

      val content = Vector(
        "/"            -> "lorem ipsum dolor amet lorem",
        "/hello"       -> "lorem dolor",
        "/hello/world" -> "amet ipsum amet dolor"
      )

      val idx    = Indexer.default[String, String](content).processAll(identity)
      val search = new Search(idx)

      def ranking(query: String, document: String) =
        search.string(query).toMap.getOrElse(document, -1.0)

      test("search by one word") {
        assert(
          search.string("lorem").map(_._1).toSet == Set(
            "/",
            "/hello"
          )
        )
      }

      test("search by two words") {
        assert(
          search.string("ipsum amet").map(_._1).toSet == Set(
            "/",
            "/hello/world"
          )
        )
      }

      test("ranking") {
        assert(
          ranking("amet", "/") > ranking("amet", "/hello")
        )
      }

    }
  }
}
