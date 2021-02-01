package subatomic
package search

class PorterStemmerTests extends munit.FunSuite {
  import PorterStemmer.stem
  test("stems?") {
    assert(stem("doing") == "do")
    assert(stem("interesting") == "interest")
    assert(stem("better") == "better")
  }
}
