package subatomic
package search

class TokenizerTests extends munit.FunSuite {

  val tokens = DefaultTokenizer

  test("handles spaces") {
    assert(
      tokens("lorem ipsum dolor amet") ==
        Vector("lorem", "ipsum", "dolor", "amet")
    )
  }

  test("handles punctuation") {
    assert(
      tokens("lorem,ipsum-dolor;amet") ==
        Vector("lorem", "ipsum", "dolor", "amet")
    )
  }

  test("handles empty string") {
    assert(tokens("") == Vector.empty)
  }

  test("handles one single token") {
    assert(tokens("loremipsumdoloramet") == Vector("loremipsumdoloramet"))
  }

  test("ignores stopwords") {
    val tokenized = tokens(Stopwords.list.mkString(" "))
    assert(tokenized == Vector.empty)
  }

  test("handles possessive") {
    assert(
      tokens("subatomic's Scala's") == Vector("subatomic'", "scala'")
    )
  }
}
