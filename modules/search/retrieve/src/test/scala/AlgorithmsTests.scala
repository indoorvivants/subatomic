package subatomic
package search

import org.scalacheck.Gen
import weaver.scalacheck.Checkers

object AlgorthimsSpec extends weaver.SimpleIOSuite with Checkers {

  // (collection size, units containing the term), 1 <= df <= N
  val collectionGen = for {
    n  <- Gen.choose(1, 10000)
    df <- Gen.choose(1, n)
  } yield (n, df)

  val tfGen = for {
    freq   <- Gen.choose(1, 1000)
    length <- Gen.choose(freq, 10000)
    avg    <- Gen.choose(1.0, 1000.0)
  } yield (freq, length, avg)

  test("IDF is always positive") {
    forall(collectionGen) { case (n, df) =>
      expect(Algorithms.bm25_IDF(n, df) > 0.0)
    }
  }

  test("IDF decreases as the term becomes more common") {
    forall(collectionGen.suchThat { case (n, df) => df < n }) { case (n, df) =>
      expect(Algorithms.bm25_IDF(n, df) > Algorithms.bm25_IDF(n, df + 1))
    }
  }

  pureTest("TF is zero for absent terms") {
    expect(Algorithms.bm25_TF(0, 10, 10.0) == 0.0)
  }

  test("TF grows with frequency, but saturates below K1 + 1") {
    forall(tfGen) { case (freq, length, avg) =>
      val current = Algorithms.bm25_TF(freq, length, avg)
      val more    = Algorithms.bm25_TF(freq + 1, length, avg)

      expect.all(
        current > 0,
        more > current,
        more < Algorithms.K1 + 1
      )
    }
  }

  test("TF is lower in longer units") {
    forall(tfGen) { case (freq, length, avg) =>
      expect(
        Algorithms.bm25_TF(freq, length, avg) >
          Algorithms.bm25_TF(freq, length + 1, avg)
      )
    }
  }
}
