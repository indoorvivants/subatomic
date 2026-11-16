package subatomic
package search

object PorterStemmerTests extends weaver.FunSuite {
  import PorterStemmer.stem
  test("stems?") {
    expect.all(
      stem("doing") == "do",
      stem("interesting") == "interest",
      stem("better") == "better"
    )
  }
}
