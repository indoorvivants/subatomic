package subatomic

import scala.util.Try

import weaver.SimpleMutableIOSuite
import weaver.Expectations
import cats.effect.IO
import cats.effect.Blocker
import weaver.Log

object MdocTests extends SimpleMutableIOSuite {
  override def maxParallelism: Int = sys.env.get("CI").map(_ => 1).getOrElse(100)

  val HelloWorldPath = SiteRoot / "hello" / "world"

  def process(content: String, dependencies: Set[String] = Set.empty, variables: Map[String, String] = Map.empty)(
      result: String => Expectations
  )(implicit log: Log[IO]): IO[Expectations] = {
    val mdoc =
      new Mdoc(logger = new Logger(s => log.info(s.replace("\n", "  ")).unsafeRunSync()), variables = variables)

    val tmpFile = os.temp(content, suffix = ".md")

    Blocker[IO].use(bl => bl.delay(mdoc.process(tmpFile, dependencies))).map { p =>
      result(os.read(p))
    }
  }

  loggedTest("mdoc works") { implicit log =>
    val content =
      """
    |hello!
    |
    |```scala mdoc
    |println("tut")
    |```""".stripMargin

    process(content) { result =>
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

  loggedTest("mdoc works with variables") { implicit log =>
    val content =
      """
    |hello!
    |
    |```scala mdoc
    |println("@HELLO@")
    |```""".stripMargin

    process(content, variables = Map("HELLO" -> "0.0.1")) { result =>
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

  loggedTest("mdoc with dependencies works") { implicit log =>
    val content =
      """
    |hello!
    |
    |```scala mdoc
    |cats.effect.IO(println("tut")).unsafeRunSync()
    |```""".stripMargin

    process(content, Set("org.typelevel::cats-effect:2.3.0")) { result =>
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
