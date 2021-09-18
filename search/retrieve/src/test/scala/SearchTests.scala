package subatomic
package search

import org.scalacheck.Gen
import org.scalacheck.Prop._

class SearchSuite extends munit.FunSuite with munit.ScalaCheckSuite {

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

  val tokenGen = Gen.oneOf(idx.termMapping.keys.map(_.value))

  val queryGen = Gen.listOfN(5, tokenGen).map(_.mkString(" "))

  val search = new Search(idx)

  def foundUrls(query: String) = search.string(query).entries.map(_._1.document.url).toSet

  def ranking(query: String, documentUrl: String) = {
    search.string(query).entries.toMap.find(_._1.document.url == documentUrl).map(_._2).getOrElse(-10000.0)
  }

  test("search by one word") {
    assertEquals(
      foundUrls("lorem"),
      Set(
        "/",
        "/hello"
      )
    )
  }

  test("search by two words") {
    assertEquals(
      foundUrls("ipsum amet"),
      Set(
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

  property("results are always in the correct order") {
    forAll(queryGen) { query =>
      val results = search.string(query)

      assertEquals(results.entries.sortBy(-1 * _._2), results.entries)
    }
  }
}
