package subatomic
package search

// import weaver.SimpleMutableIOSuite
// import weaver.scalacheck.Checkers
import org.scalacheck.Gen
// import cats.Show
import org.scalacheck.Prop._

class AlgorthimsSpec extends munit.FunSuite with munit.ScalaCheckSuite {

  val termFreqGen            = Gen.posNum[Int].map(i => TermFrequency(i))
  val termIdxGen             = Gen.posNum[Int].map(i => TermIdx(i))
  val collSizeGen            = Gen.posNum[Int].map(i => CollectionSize(i))
  val globalTermFrequencyGen = Gen.posNum[Int].map(i => GlobalTermFrequency(i))

  val documentPair = for {
    termId   <- termIdxGen
    termFreq <- termFreqGen
  } yield termId -> termFreq

  val documentGen = Gen.mapOf(documentPair)

  property("IDF is never zero") {
    forAll(collSizeGen.flatMap(cs => globalTermFrequencyGen.map(cs -> _))) {
      case (colSize, gtf) =>
        assertNotEquals(Algorithms.inverse_Document_Frequency(colSize, gtf), 0.0)
    }
  }

  property("TF is always positive") {
    forAll(documentGen) { document =>
      document.keys.toVector.foreach { termIdx =>
        assert(Algorithms.augmented_Term_Frequency(termIdx, document) > 0)
      }
    }
  }

  test("TF is safe for empty documents") {
    assert(Algorithms.augmented_Term_Frequency(TermIdx(0), Map.empty) == 0.0)
  }

  property("TF is safe for terms that don't appear in the document") {
    forAll(documentGen.suchThat(_.nonEmpty)) { document =>
      val maxTermId = document.maxBy(_._1.value)._1.value

      assertEquals(Algorithms.augmented_Term_Frequency(TermIdx(maxTermId + 1), document), 0.0)
    }
  }

  property("TF is monotonic (unless the term is already most frequent)") {
    forAll(documentGen.suchThat(_.nonEmpty)) { document =>
      val maxFreqTerm = document.maxBy(_._2.value)._2

      document.keys.toVector.foreach { termIdx =>
        val updatedDocument = document.updated(termIdx, TermFrequency(document(termIdx).value + 1))

        val current = Algorithms.augmented_Term_Frequency(termIdx, document)

        if (maxFreqTerm != document(termIdx))
          assert(
            current < Algorithms.augmented_Term_Frequency(termIdx, updatedDocument)
          )
        else {
          assert(current == 1.0)
          assert(Algorithms.augmented_Term_Frequency(termIdx, updatedDocument) == 1.0)
        }
      }
    }
  }
}

// object AlgorithmsTests extends SimpleMutableIOSuite with Checkers {

//   implicit val showColSize = Show.fromToString[CollectionSize]
//   implicit val showGTF     = Show.fromToString[GlobalTermFrequency]
//   implicit val showTF      = Show.fromToString[TermFrequency]
//   implicit val showTermIdx = Show.fromToString[TermIdx]

//   test("IDF is never zero") {
//     forall(collSizeGen.flatMap(cs => globalTermFrequencyGen.map(cs -> _))) {
//       case (colSize, gtf) =>
//         expect(Algorithms.inverse_Document_Frequency(colSize, gtf) != 0.0)
//     }
//   }

//   test("TF is always positive") {
//     forall(documentGen) { document =>
//       forEach(document.keys.toVector) { termIdx =>
//         expect(Algorithms.augmented_Term_Frequency(termIdx, document) > 0)
//       }
//     }
//   }

//   test("TF is monotonic (unless the term is already most frequent)") {
//     forall(documentGen.suchThat(_.nonEmpty)) { document =>
//       val maxFreqTerm = document.maxBy(_._2.value)._2

//       forEach(document.keys.toVector) { termIdx =>
//         val updatedDocument = document.updated(termIdx, TermFrequency(document(termIdx).value + 1))

//         val current = Algorithms.augmented_Term_Frequency(termIdx, document)

//         if (maxFreqTerm != document(termIdx))
//           expect(
//             current < Algorithms.augmented_Term_Frequency(termIdx, updatedDocument)
//           )
//         else
//           expect.all(
//             current == 1.0,
//             Algorithms.augmented_Term_Frequency(termIdx, updatedDocument) == 1.0
//           )
//       }
//     }
//   }

//   pureTest("TF is safe for empty documents") {
//     expect(Algorithms.augmented_Term_Frequency(TermIdx(0), Map.empty) == 0.0)
//   }

//   test("TF is safe for terms that don't appear in the document") {
//     forall(documentGen.suchThat(_.nonEmpty)) { document =>
//       val maxTermId = document.maxBy(_._1.value)._1.value

//       expect(Algorithms.augmented_Term_Frequency(TermIdx(maxTermId + 1), document) == 0.0)
//     }
//   }
// }
