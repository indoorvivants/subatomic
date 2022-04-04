package subatomic
package search

object PorterStemmerTests extends verify.BasicTestSuite {
  import PorterStemmer.stem
  test("stems?") {
    assert(stem("doing") == "do")
    assert(stem("interesting") == "interest")
    assert(stem("better") == "better")
  }
}
