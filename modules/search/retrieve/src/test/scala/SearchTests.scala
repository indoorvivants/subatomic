package subatomic
package search

import org.scalacheck.Gen
import weaver.scalacheck.Checkers

object SearchSuite extends weaver.SimpleIOSuite with Checkers {

  val content = Vector(
    "/"            -> "lorem ipsum dolor amet lorem",
    "/hello"       -> "lorem dolor",
    "/hello/world" -> "amet ipsum amet dolor"
  )

  val idx =
    Indexer.default[(String, String)](content).processAll { case (path, text) =>
      Document.section(
        s"Document at $path",
        path,
        text
      )
    }

  val tokenGen = Gen.oneOf(idx.termMapping.keys.map(_.value))

  val queryGen = Gen.listOfN(5, tokenGen).map(_.mkString(" "))

  val search = new Search(idx)

  def foundUrls(query: String) =
    search.string(query).entries.map(_._1.document.url).toSet

  def ranking(query: String, documentUrl: String) = {
    search
      .string(query)
      .entries
      .toMap
      .find(_._1.document.url == documentUrl)
      .map(_._2)
      .getOrElse(-10000.0)
  }

  pureTest("search by one word") {
    expect(
      foundUrls("lorem") ==
        Set(
          "/",
          "/hello"
        )
    )
  }

  pureTest("search by two words") {
    expect(
      foundUrls("ipsum amet") ==
        Set(
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

  def index(docs: Document*) =
    Indexer.default(docs.toVector).processAll(identity)

  pureTest("empty query returns nothing") {
    expect(search.string("  ").entries.isEmpty) &&
    expect(search.string("the and").entries.isEmpty)
  }

  pureTest("documents matching more of the query rank higher") {
    val idx = index(
      Document.section("One", "/one", "alpha beta gamma delta"),
      Document.section("Two", "/two", "alpha alpha alpha alpha alpha")
    )

    val urls =
      new Search(idx).string("alpha beta").entries.map(_._1.document.url)

    expect(urls == Vector("/one", "/two"))
  }

  pureTest("a term in a section title outranks the same term in body text") {
    val idx = index(
      Document(
        "Guide",
        "/guide",
        Vector(
          Section("Guide", None, "intro text"),
          Section("Configuration", Some("/guide#config"), "lots of words here")
        )
      ),
      Document.section("Other", "/other", "configuration lots of words here")
    )

    val results = new Search(idx).string("configuration").entries

    expect(results.map(_._1.document.url) == Vector("/guide", "/other")) &&
    expect(results.head._1.sections.map(_.url) == List("/guide#config"))
  }

  pureTest("a term in the document title outranks a section title") {
    val idx = index(
      Document(
        "Installation",
        "/install",
        Vector(Section("Installation", None, "how to get it"))
      ),
      Document(
        "Guide",
        "/guide",
        Vector(
          Section("Guide", None, "intro"),
          Section("Installation", Some("/guide#install"), "how to get it")
        )
      )
    )

    val urls =
      new Search(idx).string("installation").entries.map(_._1.document.url)

    expect(urls == Vector("/install", "/guide"))
  }

  pureTest("sections are ordered by score") {
    val idx = index(
      Document(
        "Doc",
        "/doc",
        Vector(
          Section("Doc", None, "unrelated"),
          // same length, so only the term frequency differs
          Section("Alpha", Some("/doc#a"), "widget foo bar"),
          Section("Beta", Some("/doc#b"), "widget widget widget")
        )
      )
    )

    val sections = new Search(idx).string("widget").entries.head._1.sections

    expect(sections.map(_.url) == List("/doc#b", "/doc#a"))
  }
}
