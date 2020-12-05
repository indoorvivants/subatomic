package subatomic
package search

import weaver.SimpleMutableIOSuite

object SearchTests extends SimpleMutableIOSuite {
  val content = Vector(
    "/"            -> "lorem ipsum dolor amet lorem",
    "/hello"       -> "lorem dolor",
    "/hello/world" -> "amet ipsum amet dolor"
  )

  val idx    = Indexer.default[String, String](content).processAll(identity)
  val search = new Search(idx)

  def ranking(query: String, document: String) =
    search.string(query).toMap.getOrElse(document, -1.0)

  pureTest("search by one word") {
    expect(
      search.string("lorem").map(_._1).toSet == Set(
        "/",
        "/hello"
      )
    )
  }

  pureTest("search by two words") {
    expect(
      search.string("ipsum amet").map(_._1).toSet == Set(
        "/",
        "/hello/world"
      )
    )
  }

  pureTest("ranking") {
    expect(
      ranking("amet", "/") > ranking("amet", "/hello")
    )
  }

}
