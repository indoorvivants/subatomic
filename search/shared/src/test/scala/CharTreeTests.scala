package subatomic
package search

import cats.Show
import org.scalacheck.Gen
import weaver.PureIOSuite
import weaver.SimpleMutableIOSuite
import weaver.scalacheck.Checkers

object CharTreeTests extends SimpleMutableIOSuite with Checkers {
  override def maxParallelism: Int = 1

  val MostCommonEnglishBigrams =
    ("th,en,ng,he,ed,of,in,to,al,er,it,de,an,ou,se," +
      "re,ea,le,nd,hi,sa,at,is,si,on,or,ar,nt,ti,ve," +
      "ha,as,ra,es,te,ld,st,et,ur")
      .split(",")
      .toVector

  val wordGen: Gen[String] = for {
    wordSize <- Gen.choose(1, 10)
    bigram = Gen.oneOf(MostCommonEnglishBigrams)
    randomWord <- Gen.listOfN(wordSize, bigram)
  } yield randomWord.mkString

  val MaxSize =
    if (subatomic.internal.BuildInfo.platform == "js") 100 else 10000

  val gen = for {
    size  <- Gen.choose(10, MaxSize)
    words <- Gen.listOfN(size, wordGen)
    dataset = words.distinct.zipWithIndex.map {
      case (n, idx) => TermName(n) -> TermIdx(idx)
    }
  } yield (CharTree.build(dataset), dataset)

  val seed = scala.util.Random.nextLong()

  implicit val showDs: Show[(CharTree, List[(TermName, TermIdx)])] =
    Show.fromToString

  test("CharTree build and retrieval") {
    forall(gen) {
      case ((chartree, dataset)) =>
        val resolutions = dataset.map {
          case (tn, tidx) =>
            (tidx, chartree.find(tn))
        }

        val (_, notFound) = resolutions.partition {
          case (expected, result) =>
            result.contains(expected)
        }

        expect(notFound.isEmpty)
    }
  }
}
