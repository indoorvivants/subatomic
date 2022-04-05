package subatomic
package search

object IndexerSuite extends verify.BasicTestSuite {

  val content = Vector(
    "/" ->
      List("section1" -> "lorem ipsum dolor amet lorem"),
    "/hello"       -> List("section1" -> "lorem dolor"),
    "/hello/world" -> List("section1" -> "amet ipsum amet dolor")
  )

  val idx =
    Indexer.default(content).processAll {
      case (
            path,
            sections
          ) => // Document.section(s"Document at $path", path, text)

        Document(
          s"Document at $path",
          path,
          sections.map { case (title, content) =>
            Section(title, None, content)
          }.toVector
        )
    }

  test("all documents have entry in the index") {
    assert(
      idx.documentsMapping.values.map(_.url).toSet ==
        content
          .map(_._1)
          .toSet
    )
  }
  test("all words have entry in the index") {
    val allWords = Set("lorem", "ipsum", "dolor", "amet")

    assert(
      idx.termMapping.keySet.map(_.value) ==
        allWords
    )
  }

  test("all terms have entries in the terms-to-documents mapping") {
    def getMapping(term: String) =
      idx.termsInDocuments(idx.termMapping(TermName(term)))

    assert(getMapping("lorem").nonEmpty)
    assert(getMapping("ipsum").nonEmpty)
    assert(getMapping("dolor").nonEmpty)
    assert(getMapping("amet").nonEmpty)
  }

  test("in document frequency is correctly calculated") {
    def getInDocumentFrequency(term: String, url: String) = {
      val termIdx = idx.termMapping(TermName(term))
      val docIdx = idx
        .documentByUrl(url)
        .getOrElse(err(s"Document with $url not found in the index"))

      idx.termsInDocuments(termIdx)(docIdx).frequencyInDocument.value
    }

    assert(getInDocumentFrequency("lorem", "/") == 2)
    assert(getInDocumentFrequency("ipsum", "/") == 1)
    assert(getInDocumentFrequency("dolor", "/") == 1)
    assert(getInDocumentFrequency("amet", "/") == 1)
    assert(getInDocumentFrequency("lorem", "/hello") == 1)
    assert(getInDocumentFrequency("dolor", "/hello") == 1)
    assert(getInDocumentFrequency("amet", "/hello/world") == 2)
    assert(getInDocumentFrequency("ipsum", "/hello/world") == 1)
    assert(getInDocumentFrequency("dolor", "/hello/world") == 1)
  }

  test("document index is correct") {
    def getDocumentTerms(url: String) = {
      val docIdx = idx
        .documentByUrl(url)
        .getOrElse(err(s"Document with $url not found in the index"))
      val reverseTermMapping = idx.termMapping.map(_.swap)

      idx.documentTerms(docIdx).map { case (termIdx, termFreq) =>
        reverseTermMapping(
          termIdx
        ).value -> termFreq.frequencyInDocument.value
      }
    }

    assert(
      getDocumentTerms("/") ==
        Map(
          "lorem" -> 2,
          "ipsum" -> 1,
          "dolor" -> 1,
          "amet"  -> 1
        )
    )

    assert(
      getDocumentTerms("/hello") ==
        Map(
          "lorem" -> 1,
          "dolor" -> 1
        )
    )
    assert(
      getDocumentTerms("/hello/world") ==
        Map(
          "ipsum" -> 1,
          "dolor" -> 1,
          "amet"  -> 2
        )
    )
  }

  def err(msg: String): Nothing = throw new Exception(msg)

  implicit class SearchIndexOps(idx: SearchIndex) {
    def documentByUrl(url: String): Option[DocumentIdx] = {
      val entry = idx.documentsMapping.find(_._2.url == url)

      entry.map(_._1)
    }

  }
}
