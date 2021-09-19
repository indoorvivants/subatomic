package subatomic

object Scala3MdocTests extends weaver.IOSuite with subatomic.MdocTestHarness {
  test("mdoc works with scala 3 syntax") { (res, log) =>
    val content =
      """
    |hello!
    |
    |```scala mdoc
    |trait X[T]:
    |  def convert(t: T): Int
    |
    |if 25 > 15 then
    |  println("hello")
    |  println("world")
    |println("tut")
    |```""".stripMargin

    res.process(content, log = log) { result =>
      val expected =
        """
    |hello!
    |
    |```scala
    |trait X[T]:
    |  def convert(t: T): Int
    |
    |if 25 > 15 then
    |  println("hello")
    |  println("world")
    |// hello
    |// world
    |println("tut")
    |// tut
    |```""".stripMargin

      expect.same(result, expected)
    }
  }

}
