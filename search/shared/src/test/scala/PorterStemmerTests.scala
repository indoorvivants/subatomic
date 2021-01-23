package subatomic
package search

import cats.Show
import org.scalacheck.Gen
import weaver.PureIOSuite
import weaver.SimpleMutableIOSuite
import weaver.scalacheck.Checkers

object PorterStemmerTests extends SimpleMutableIOSuite with Checkers {
  import PorterStemmer.stem
  pureTest("stems?") {
    expect.all(
      stem("doing") == "do",
      stem("interesting") == "interest",
      stem("better") == "better"
    )
  }
}
