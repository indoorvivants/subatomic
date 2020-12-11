package subatomic
package search

import weaver.SimpleMutableIOSuite

object SearchTests extends SimpleMutableIOSuite {
  val content = Vector(
    "/"            -> "lorem ipsum dolor amet lorem",
    "/hello"       -> "lorem dolor",
    "/hello/world" -> "amet ipsum amet dolor"
  )

  val idx = Indexer.default[(String, String)](content).processAll {
    case (path, text) =>
      Document.section(
        s"Document at $path",
        path,
        text
      )
  }
  
  val search = new Search(idx)

  def foundUrls(query: String) = search.string(query).map(_._1.url).toSet

  def ranking(query: String, documentUrl: String) ={
    search.string(query).toMap.find(_._1.url == documentUrl).map(_._2).getOrElse(-1.0)
  }

  pureTest("search by one word") {
    expect(
      foundUrls("lorem") == Set(
        "/",
        "/hello"
      )
    )
  }

  pureTest("search by two words") {
    expect(
      foundUrls("ipsum amet") == Set(
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
