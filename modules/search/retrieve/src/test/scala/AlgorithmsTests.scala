package subatomic
package search

import cats.Show
import org.scalacheck.Gen
import weaver.scalacheck.Checkers

object AlgorthimsSpec extends weaver.SimpleIOSuite with Checkers {

  implicit val showTermIdx: Show[TermIdx]               = Show.fromToString
  implicit val showTermFrequency: Show[TermFrequency]   = Show.fromToString
  implicit val showCollectionSize: Show[CollectionSize] = Show.fromToString
  implicit val showGlobalTermFrequency: Show[GlobalTermFrequency] =
    Show.fromToString

  val termFreqGen            = Gen.posNum[Int].map(i => TermFrequency(i))
  val termIdxGen             = Gen.posNum[Int].map(i => TermIdx(i))
  val collSizeGen            = Gen.posNum[Int].map(i => CollectionSize(i))
  val globalTermFrequencyGen = Gen.posNum[Int].map(i => GlobalTermFrequency(i))

  val documentPair = for {
    termId   <- termIdxGen
    termFreq <- termFreqGen
  } yield termId -> termFreq

  val documentGen = Gen.mapOf(documentPair)

  test("IDF is never zero") {
    forall(collSizeGen.flatMap(cs => globalTermFrequencyGen.map(cs -> _))) {
      case (colSize, gtf) =>
        expect(Algorithms.inverse_Document_Frequency(colSize, gtf) != 0.0)
    }
  }

  test("TF is always positive") {
    forall(documentGen) { document =>
      forEach(document.keys.toVector) { termIdx =>
        expect(Algorithms.augmented_Term_Frequency(termIdx, document) > 0)
      }
    }
  }

  pureTest("TF is safe for empty documents") {
    expect(Algorithms.augmented_Term_Frequency(TermIdx(0), Map.empty) == 0.0)
  }

  test("TF is safe for terms that don't appear in the document") {
    forall(documentGen.suchThat(_.nonEmpty)) { document =>
      val maxTermId = document.maxBy(_._1.value)._1.value

      expect(
        Algorithms.augmented_Term_Frequency(TermIdx(maxTermId + 1), document) ==
          0.0
      )
    }
  }

  test("TF is monotonic (unless the term is already most frequent)") {
    forall(documentGen.suchThat(_.nonEmpty)) { document =>
      val maxFreqTerm = document.maxBy(_._2.value)._2

      forEach(document.keys.toVector) { termIdx =>
        val updatedDocument =
          document.updated(termIdx, TermFrequency(document(termIdx).value + 1))

        val current = Algorithms.augmented_Term_Frequency(termIdx, document)

        if (maxFreqTerm != document(termIdx))
          expect(
            current < Algorithms
              .augmented_Term_Frequency(termIdx, updatedDocument)
          )
        else
          expect.all(
            current == 1.0,
            Algorithms.augmented_Term_Frequency(termIdx, updatedDocument) == 1.0
          )
      }
    }
  }
}
