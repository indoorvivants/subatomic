package subatomic
package search

import weaver.PureIOSuite
import weaver.SimpleMutableIOSuite

object TokenizerTests extends SimpleMutableIOSuite {

  override def maxParallelism: Int = 1

  val tokens = DefaultTokenizer

  pureTest("handles spaces") {
    assert(
      tokens("lorem ipsum dolor amet") ==
        Vector("lorem", "ipsum", "dolor", "amet")
    )
  }

  pureTest("handles punctuation") {
    assert(
      tokens("lorem,ipsum-dolor;amet") ==
        Vector("lorem", "ipsum", "dolor", "amet")
    )
  }

  pureTest("handles empty string") {
    assert(tokens("") == Vector.empty)
  }

  pureTest("handles one single token") {
    assert(tokens("loremipsumdoloramet") == Vector("loremipsumdoloramet"))
  }

  pureTest("ignores stopwords") {
    val tokenized = tokens(Stopwords.list.mkString(" "))
    assert(tokenized == Vector.empty)
  }

  pureTest("handles possessive") {
    assert(
      tokens("subatomic's Scala's") == Vector("subatomic's", "scala's")
    )
  }
}
