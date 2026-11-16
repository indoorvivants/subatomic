package subatomic
package search

object TokenizerTests extends weaver.FunSuite {

  val tokens = DefaultTokenizer

  test("handles spaces") {
    expect(
      tokens("lorem ipsum dolor amet") ==
        Vector("lorem", "ipsum", "dolor", "amet")
    )
  }

  test("handles punctuation") {
    expect(
      tokens("lorem,ipsum-dolor;amet") ==
        Vector("lorem", "ipsum", "dolor", "amet")
    )
  }

  test("handles empty string") {
    expect(tokens("") == Vector.empty)
  }

  test("handles one single token") {
    expect(tokens("loremipsumdoloramet") == Vector("loremipsumdoloramet"))
  }

  test("ignores stopwords") {
    val tokenized = tokens(Stopwords.list.mkString(" "))
    expect(tokenized == Vector.empty)
  }

  test("handles possessive") {
    expect(
      tokens("subatomic's Scala's") == Vector("subatomic'", "scala'")
    )
  }
}
