package subatomic
package search

import org.scalacheck.Gen
import org.scalacheck.Prop._

object AlgorthimsSpec extends verify.BasicTestSuite {

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
    forAll(collSizeGen.flatMap(cs => globalTermFrequencyGen.map(cs -> _))) {
      case (colSize, gtf) =>
        Algorithms.inverse_Document_Frequency(colSize, gtf) != 0.0
    }.check()
  }

  test("TF is always positive") {
    forAll(documentGen) { document =>
      document.keys.toVector.forall { termIdx =>
        Algorithms.augmented_Term_Frequency(termIdx, document) > 0
      }
    }.check()
  }

  test("TF is safe for empty documents") {
    assert(Algorithms.augmented_Term_Frequency(TermIdx(0), Map.empty) == 0.0)
  }

  test("TF is safe for terms that don't appear in the document") {
    forAll(documentGen.suchThat(_.nonEmpty)) { document =>
      val maxTermId = document.maxBy(_._1.value)._1.value

      Algorithms.augmented_Term_Frequency(TermIdx(maxTermId + 1), document) ==
        0.0
    }.check()
  }

  test("TF is monotonic (unless the term is already most frequent)") {
    forAll(documentGen.suchThat(_.nonEmpty)) { document =>
      val maxFreqTerm = document.maxBy(_._2.value)._2

      document.keys.toVector.forall { termIdx =>
        val updatedDocument =
          document.updated(termIdx, TermFrequency(document(termIdx).value + 1))

        val current = Algorithms.augmented_Term_Frequency(termIdx, document)

        if (maxFreqTerm != document(termIdx))
          current < Algorithms
            .augmented_Term_Frequency(termIdx, updatedDocument)
        else {
          current == 1.0 &&
          Algorithms.augmented_Term_Frequency(termIdx, updatedDocument) == 1.0
        }
      }
    }.check()
  }
}
