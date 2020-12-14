package subatomic

import scala.util.Try

import weaver.SimpleMutableIOSuite
import weaver.Expectations
import cats.effect.IO
import cats.effect.Blocker
import weaver.Log
import cats.effect.concurrent.Ref
import cats.data.Chain

import cats.effect.syntax._
import cats.syntax.all._

object MdocBatchTests extends SimpleMutableIOSuite {

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

  loggedTest("prepared Mdoc invoked only once") { implicit log =>
    val content =
      """
    |hello!
    |
    |```scala mdoc
    |println("tut")
    |```""".stripMargin

    for {
      logs <- Ref.of[IO, Chain[String]](Chain.empty)

      mdoc = new Mdoc(
        logger = new Logger(s => (logs.update(_ ++ Chain(s)) *> log.info(s.replace("\n", "  "))).unsafeRunSync())
      )

      tmpContent = List.fill(10)(os.temp(content, suffix = ".md"))

      prepared = mdoc.prepare(tmpContent.map { path =>
        path -> MdocFile(path)
      })

      firstRetrieved <- Blocker[IO].use { bl =>
        bl.delay(prepared.get(tmpContent.head)).map(os.read)
      }

      allRetrieved <- tmpContent.parTraverse(path => IO(os.read(prepared.get(path)))).map(_.distinct)
      results      <- logs.get
    } yield expect(results.exists(_.contains("Compiling 10 files to"))) and
      expect(results.toList.count(_.contains("Compiling")) == 1) and
      expect(allRetrieved == List(firstRetrieved))
  }

  loggedTest("prepared Mdoc invoked only once per dependency set") { implicit log =>
    val content1 =
      """
    |hello!
    |
    |```scala mdoc
    |println("tut")
    |```""".stripMargin

    val content2 =
      """
    |hello!
    |
    |```scala mdoc
    |cats.effect.IO(println("tut")).unsafeRunSync()
    |```""".stripMargin

    val zeroDepContent = List.fill(5)(os.temp(content1, suffix = ".zero-dep.md"))
    val ceDepContent   = List.fill(5)(os.temp(content2, suffix = "cats-effect..md"))

    val preparedZeroDep = zeroDepContent.map { path =>
      path -> MdocFile(path)
    }

    val preparedCEDep = ceDepContent.map { path =>
      path -> MdocFile(path, dependencies = Set("org.typelevel::cats-effect:2.3.0"))
    }

    for {
      logs <- Ref.of[IO, Chain[String]](Chain.empty)

      mdoc = new Mdoc(
        logger = new Logger(s => (logs.update(_ ++ Chain(s)) *> log.info(s.replace("\n", "  "))).unsafeRunSync())
      )

      prepared = mdoc.prepare(preparedZeroDep ++ preparedCEDep)

      resultFirst <- Blocker[IO].use { bl =>
        (
          bl.delay(prepared.get(zeroDepContent.head)).map(os.read),
          bl.delay(prepared.get(ceDepContent.head)).map(os.read)
        ).parTupled
      }

      firstRetrievedZeroDep = resultFirst._1
      firstRetrievedCEDep   = resultFirst._2

      allRetrievedZeroDep <- zeroDepContent.parTraverse(path => IO(os.read(prepared.get(path)))).map(_.distinct)
      allRetrievedCEDep   <- ceDepContent.parTraverse(path => IO(os.read(prepared.get(path)))).map(_.distinct)

      results <- logs.get.map(_.toList)
    } yield expect(results.count(_.contains("Compiling 5 files to")) == 2) and
      expect(results.count(_.contains("Compiling")) == 2) and
      expect(allRetrievedZeroDep == List(firstRetrievedZeroDep)) and
      expect(allRetrievedCEDep == List(firstRetrievedCEDep))
  }

  loggedTest("prepared Mdoc works if all are requested at once") { implicit log =>
    val content =
      """
    |hello!
    |
    |```scala mdoc
    |println("tut")
    |```""".stripMargin

    for {
      logs <- Ref.of[IO, Chain[String]](Chain.empty)

      mdoc = new Mdoc(
        logger = new Logger(s => (logs.update(_ ++ Chain(s)) *> log.info(s.replace("\n", "  "))).unsafeRunSync())
      )

      tmpContent = List.fill(10)(os.temp(content, suffix = ".md"))

      prepared = mdoc.prepare(tmpContent.map { path =>
        path -> MdocFile(path)
      })

      allRetrieved <- tmpContent.parTraverse(path => IO(os.read(prepared.get(path)))).map(_.distinct)
      results      <- logs.get
    } yield expect(results.exists(_.contains("Compiling 10 files to"))) and
      expect(results.toList.count(_.contains("Compiling")) == 1) and
      expect(allRetrieved.size == 1)
  }
}