package subatomic
package search

import weaver.SimpleMutableIOSuite
import weaver.scalacheck.Checkers
import org.scalacheck.Gen

object SearchTests extends SimpleMutableIOSuite with Checkers {
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

  val tokenGen = Gen.oneOf(idx.termMapping.keys.map(_.value))

  val queryGen = Gen.listOfN(5, tokenGen).map(_.mkString(" "))

  val search = new Search(idx)

  def foundUrls(query: String) = search.string(query).entries.map(_._1.document.url).toSet

  def ranking(query: String, documentUrl: String) = {
    search.string(query).entries.toMap.find(_._1.document.url == documentUrl).map(_._2).getOrElse(-10000.0)
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

  test("results are always in the correct order") {
    forall(queryGen) { query =>
      val results = search.string(query)

      expect(results.entries.sortBy(-1 * _._2) == results.entries)
    }
  }

}
