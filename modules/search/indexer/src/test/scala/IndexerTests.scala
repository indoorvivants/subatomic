package subatomic
package search

object IndexerSuite extends weaver.FunSuite {

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
    expect(
      idx.documentsMapping.values.map(_.url).toSet ==
        content
          .map(_._1)
          .toSet
    )
  }
  test("all words have entry in the index") {
    // body words, plus the section title ("section1") and the words of
    // document titles ("Document at /hello/world")
    val allWords =
      Set("lorem", "ipsum", "dolor", "amet", "section1", "document", "hello", "world")

    expect(
      idx.termMapping.keySet.map(_.value) ==
        allWords
    )
  }

  test("all terms have entries in the terms-to-documents mapping") {
    def getMapping(term: String) =
      idx.termsInDocuments(idx.termMapping(TermName(term)))

    expect.all(
      getMapping("lorem").nonEmpty,
      getMapping("ipsum").nonEmpty,
      getMapping("dolor").nonEmpty,
      getMapping("amet").nonEmpty
    )
  }

  test("in document frequency is correctly calculated") {
    def getInDocumentFrequency(term: String, url: String) = {
      val termIdx = idx.termMapping(TermName(term))
      val docIdx  = idx
        .documentByUrl(url)
        .getOrElse(err(s"Document with $url not found in the index"))

      idx.termsInDocuments(termIdx)(docIdx).frequencyInDocument.value
    }

    expect.all(
      getInDocumentFrequency("lorem", "/") == 2,
      getInDocumentFrequency("ipsum", "/") == 1,
      getInDocumentFrequency("dolor", "/") == 1,
      getInDocumentFrequency("amet", "/") == 1,
      getInDocumentFrequency("lorem", "/hello") == 1,
      getInDocumentFrequency("dolor", "/hello") == 1,
      getInDocumentFrequency("amet", "/hello/world") == 2,
      getInDocumentFrequency("ipsum", "/hello/world") == 1,
      getInDocumentFrequency("dolor", "/hello/world") == 1
    )
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

    expect(
      getDocumentTerms("/") ==
        Map(
          "lorem"    -> 2,
          "ipsum"    -> 1,
          "dolor"    -> 1,
          "amet"     -> 1,
          "section1" -> Indexer.SectionTitleWeight,
          "document" -> Indexer.DocumentTitleWeight
        )
    ) &&
    expect(
      getDocumentTerms("/hello") ==
        Map(
          "lorem"    -> 1,
          "dolor"    -> 1,
          "section1" -> Indexer.SectionTitleWeight,
          "document" -> Indexer.DocumentTitleWeight,
          "hello"    -> Indexer.DocumentTitleWeight
        )
    ) &&
    expect(
      getDocumentTerms("/hello/world") ==
        Map(
          "ipsum"    -> 1,
          "dolor"    -> 1,
          "amet"     -> 2,
          "section1" -> Indexer.SectionTitleWeight,
          "document" -> Indexer.DocumentTitleWeight,
          "hello"    -> Indexer.DocumentTitleWeight,
          "world"    -> Indexer.DocumentTitleWeight
        )
    )
  }

  val multiSection = Document(
    "Guide",
    "/guide",
    Vector(
      Section("Guide", None, "widget intro"),
      Section("Usage", Some("/guide#usage"), "widget widget usage")
    )
  )

  test("global term frequency counts documents, not sections") {
    val idx = Indexer
      .default(Vector(multiSection, Document.section("x", "/x", "widget")))
      .processAll(identity)

    val widget = idx.termMapping(TermName("widget"))

    expect(idx.globalTermFrequency(widget).value == 2)
  }

  test("collection size only counts indexed documents") {
    val idx = Indexer
      .default(Vector("/a" -> true, "/b" -> false, "/c" -> false))
      .processSome { case (url, true) => Document.section("t", url, "text") }

    expect(idx.collectionSize.value == 1)
  }

  test("titles are weighted, document title only counts once") {
    val idx = Indexer.default(Vector(multiSection)).processAll(identity)

    def sectionFreqs(term: String) =
      idx
        .termsInDocuments(idx.termMapping(TermName(term)))
        .values
        .head
        .sectionOccurences
        .map { case (k, v) => k.value -> v.value }

    expect.all(
      // first section's title equals the document title: not double-counted
      sectionFreqs("guid") == Map(0 -> Indexer.DocumentTitleWeight),
      sectionFreqs("usag") == Map(1 -> (Indexer.SectionTitleWeight + 1)),
      sectionFreqs("widget") == Map(0 -> 1, 1 -> 2)
    )
  }

  test("section lengths include title weights") {
    val idx = Indexer.default(Vector(multiSection)).processAll(identity)

    expect(
      idx.sectionLengths.values.head ==
        Vector(
          Indexer.DocumentTitleWeight + 2,
          Indexer.SectionTitleWeight + 3
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
