package subatomic

import scala.util.Try

import weaver.SimpleMutableIOSuite
import weaver.Expectations
import cats.effect.IO
import weaver.Log
import weaver.MutableIOSuite
import cats.effect.Blocker
import cats.effect.Resource

object MdocTests extends MutableIOSuite {
  override type Res = Processor
  override def sharedResource: Resource[IO, Res] = Blocker[IO].map(new Processor(_))
  override def maxParallelism: Int               = sys.env.get("CI").map(_ => 1).getOrElse(100)

  val HelloWorldPath = SiteRoot / "hello" / "world"

  class Processor(
      blocker: Blocker
  ) {
    def process(
        content: String,
        dependencies: Set[String] = Set.empty,
        variables: Map[String, String] = Map.empty,
        log: Log[IO]
    )(
        result: String => Expectations
    ): IO[Expectations] = {
      val logger = new Logger(s => log.info(s.replace("\n", "  ")).unsafeRunSync())
      val mdoc =
        new Mdoc(logger = logger, variables = variables)

      val tmpFile = os.temp(content, suffix = ".md")

      blocker.blockOn(IO(mdoc.process(tmpFile, dependencies))).map { p =>
        result(os.read(p))
      }
    }
  }

  test("mdoc works") { (res, log) =>
    val content =
      """
    |hello!
    |
    |```scala mdoc
    |println("tut")
    |```""".stripMargin

    res.process(content, log = log) { result =>
      val expected =
        """
      |hello!
      |
      |```scala
      |println("tut")
      |// tut
      |```""".stripMargin

      expect(result == expected)
    }
  }

  test("mdoc works with variables") { (res, log) =>
    val content =
      """
    |hello!
    |
    |```scala mdoc
    |println("@HELLO@")
    |```""".stripMargin

    res.process(content, variables = Map("HELLO" -> "0.0.1"), log = log) { result =>
      val expected =
        """
      |hello!
      |
      |```scala
      |println("0.0.1")
      |// 0.0.1
      |```""".stripMargin

      expect(result == expected)
    }
  }

  test("mdoc with dependencies works") { (res, log) =>
    val content =
      """
    |hello!
    |
    |```scala mdoc
    |cats.effect.IO(println("tut")).unsafeRunSync()
    |```""".stripMargin

    res.process(content, Set("org.typelevel::cats-effect:2.3.0"), log = log) { result =>
      val expected =
        """
      |hello!
      |
      |```scala
      |cats.effect.IO(println("tut")).unsafeRunSync()
      |// tut
      |```""".stripMargin

      expect(result == expected)
    }
  }
}
