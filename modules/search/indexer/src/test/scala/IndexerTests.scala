package subatomic
package search

// import weaver.PureIOSuite
// import weaver.SimpleMutableIOSuite

class IndexerSuite extends munit.FunSuite {

  val content = Vector(
    "/" ->
      List("section1" -> "lorem ipsum dolor amet lorem"),
    "/hello"       -> List("section1" -> "lorem dolor"),
    "/hello/world" -> List("section1" -> "amet ipsum amet dolor")
  )

  val idx =
    Indexer.default(content).processAll { case (path, sections) => //Document.section(s"Document at $path", path, text)

      Document(
        s"Document at $path",
        path,
        sections.map { case (title, content) =>
          Section(title, None, content)
        }.toVector
      )
    }

  test("all documents have entry in the index") {
    assertEquals(
      idx.documentsMapping.values.map(_.url).toSet,
      content
        .map(_._1)
        .toSet
    )
  }
  test("all words have entry in the index") {
    val allWords = Set("lorem", "ipsum", "dolor", "amet")

    assertEquals(
      idx.termMapping.keySet.map(_.value),
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

    assertEquals(
      getDocumentTerms("/"),
      Map(
        "lorem" -> 2,
        "ipsum" -> 1,
        "dolor" -> 1,
        "amet"  -> 1
      )
    )

    assertEquals(
      getDocumentTerms("/hello"),
      Map(
        "lorem" -> 1,
        "dolor" -> 1
      )
    )
    assertEquals(
      getDocumentTerms("/hello/world"),
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

// object IndexerTests extends SimpleMutableIOSuite {

//   val content = Vector(
//     "/" ->
//       List("section1" -> "lorem ipsum dolor amet lorem"),
//     "/hello"       -> List("section1" -> "lorem dolor"),
//     "/hello/world" -> List("section1" -> "amet ipsum amet dolor")
//   )

//   val idx = Indexer.default(content).processAll {
//     case (path, sections) => //Document.section(s"Document at $path", path, text)

//       Document(
//         s"Document at $path",
//         path,
//         sections.map {
//           case (title, content) =>
//             Section(title, None, content)
//         }.toVector
//       )
//   }

// pureTest("all documents have entry in the index") {
//   expect(
//     idx.documentsMapping.values.map(_.url).toSet == content
//       .map(_._1)
//       .toSet
//   )
// }

// pureTest("all words have entry in the index") {
//   val allWords = Set("lorem", "ipsum", "dolor", "amet")

//   expect(
//     idx.termMapping.keySet.map(_.value) == allWords
//   )
// }

// pureTest("all terms have entries in the terms-to-documents mapping") {
//   def getMapping(term: String) =
//     idx.termsInDocuments(idx.termMapping(TermName(term)))

//   expect.all(
//     getMapping("lorem").nonEmpty,
//     getMapping("ipsum").nonEmpty,
//     getMapping("dolor").nonEmpty,
//     getMapping("amet").nonEmpty
//   )
// }

// pureTest("global term frequency is correctly calculated") {
//   def getGlobalFrequency(term: String) =
//     idx.termsInDocuments(idx.termMapping(TermName(term))).size

//   expect.all(
//     getGlobalFrequency("lorem") == 2,
//     getGlobalFrequency("ipsum") == 2,
//     getGlobalFrequency("dolor") == 3,
//     getGlobalFrequency("amet") == 2
//   )
// }

// pureTest("in document frequency is correctly calculated") {
//   def getInDocumentFrequency(term: String, url: String) = {
//     val termIdx = idx.termMapping(TermName(term))
//     val docIdx = idx
//       .documentByUrl(url)
//       .getOrElse(err(s"Document with $url not found in the index"))

//     idx.termsInDocuments(termIdx)(docIdx).frequencyInDocument.value
//   }

//   expect.all(
//     getInDocumentFrequency("lorem", "/") == 2,
//     getInDocumentFrequency("ipsum", "/") == 1,
//     getInDocumentFrequency("dolor", "/") == 1,
//     getInDocumentFrequency("amet", "/") == 1,
//     getInDocumentFrequency("lorem", "/hello") == 1,
//     getInDocumentFrequency("dolor", "/hello") == 1,
//     getInDocumentFrequency("amet", "/hello/world") == 2,
//     getInDocumentFrequency("ipsum", "/hello/world") == 1,
//     getInDocumentFrequency("dolor", "/hello/world") == 1
//   )
// }

// pureTest("document index is correct") {
//   def getDocumentTerms(url: String) = {
//     val docIdx = idx
//       .documentByUrl(url)
//       .getOrElse(err(s"Document with $url not found in the index"))
//     val reverseTermMapping = idx.termMapping.map(_.swap)

//     idx.documentTerms(docIdx).map {
//       case (termIdx, termFreq) =>
//         reverseTermMapping(
//           termIdx
//         ).value -> termFreq.frequencyInDocument.value
//     }
//   }

//   expect.all(
//     getDocumentTerms("/") == Map(
//       "lorem" -> 2,
//       "ipsum" -> 1,
//       "dolor" -> 1,
//       "amet"  -> 1
//     ),
//     getDocumentTerms("/hello") == Map(
//       "lorem" -> 1,
//       "dolor" -> 1
//     ),
//     getDocumentTerms("/hello/world") == Map(
//       "ipsum" -> 1,
//       "dolor" -> 1,
//       "amet"  -> 2
//     )
//   )
// }

// def err(msg: String): Nothing = throw new Exception(msg)

// implicit class SearchIndexOps(idx: SearchIndex) {
//   def documentByUrl(url: String): Option[DocumentIdx] = {
//     val entry = idx.documentsMapping.find(_._2.url == url)

//     entry.map(_._1)
//   }

// }
// }
