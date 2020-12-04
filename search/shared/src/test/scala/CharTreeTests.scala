package subatomic
package search

import utest._

object CharTreeTests extends TestSuite {

  val MostCommonEnglishBigrams =
    "th,en,ng,he,ed,of,in,to,al,er,it,de,an,ou,se," +
      "re,ea,le,nd,hi,sa,at,is,si,on,or,ar,nt,ti,ve," +
      "ha,as,ra,es,te,ld,st,et,ur"
        .split(",")
        .toVector

  def runTest() = {
    def size       = 100 + scala.util.Random.nextInt(100000)
    def numBigrams = scala.util.Random.nextInt(10) + 1
    def randomBigram =
      MostCommonEnglishBigrams(
        scala.util.Random.nextInt(MostCommonEnglishBigrams.size)
      )
    def randomWord = List.fill(numBigrams)(randomBigram).mkString

    val words =
      List.fill(size)(randomWord).distinct

    val dataset = words.zipWithIndex.map {
      case (n, idx) => TermName(n) -> TermIdx(idx)
    }

    val tree = CharTree.build(dataset)

    dataset.foreach {
      case (tn, tidx) =>
        val foundIdx = tree.find(tn)
        assert(foundIdx == Some(tidx))
    }
  }

  val seed = scala.util.Random.nextLong()

  val tests = Tests {
    test("basic") {
      println(s"Seed: $seed")
      scala.util.Random.setSeed(seed)
      test("probably") { runTest() }
      test("should've") { runTest() }
      test("used") { runTest() }
      test("scalacheck") { runTest() }
    }
  }
}
