package subatomic


import weaver._
import cats.effect.IO

import cats.effect.Blocker
import cats.effect.Resource

object MdocBatchTests extends IOSuite {
  override type Res = Processor
  override def sharedResource: Resource[IO, Res] = Blocker[IO].map(new Processor(_))

  override def maxParallelism: Int = sys.env.get("CI").map(_ => 1).getOrElse(100)

  val HelloWorldPath = SiteRoot / "hello" / "world"

  class Processor(
      val blocker: Blocker
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

      val config = MdocConfiguration.default.copy(extraDependencies = dependencies, variables = variables)

      val mdoc =
        new Mdoc(logger, config)

      val tmpFile = os.temp(content, suffix = ".md")

      blocker.blockOn(IO(mdoc.process(tmpFile))).map { p =>
        result(os.read(p))
      }
    }

    def block[A](a: => A) = blocker.blockOn(IO(a))
  }

  // test("prepared Mdoc invoked only once") { (res, log) =>
  //   val content =
  //     """
  //   |hello!
  //   |
  //   |```scala mdoc
  //   |println("tut")
  //   |```""".stripMargin

  //   for {
  //     logs <- Ref.of[IO, Chain[String]](Chain.empty[String])

  //     logger = new Logger(s => effectCompat.sync(logs.update(_ ++ Chain(s)) *> log.info(s.replace("\n", "  "))))
  //     mdoc   = new Mdoc(logger = logger)

  //     tmpContent = List.fill(10)(os.temp(content, suffix = ".md"))

  //     prepared = mdoc.prepare(tmpContent.map { path =>
  //       path -> MdocFile(path, MdocConfiguration.default)
  //     })

  //     firstRetrieved <- res.block(prepared.get(tmpContent.head)).map(os.read)

  //     allRetrieved <- tmpContent.parTraverse(path => IO(os.read(prepared.get(path)))).map(_.distinct)
  //     results      <- logs.get
  //   } yield expect(results.exists(_.contains("Compiling 10 files to"))) and
  //     expect(results.toList.count(_.contains("Compiling")) == 1) and
  //     expect(allRetrieved == List(firstRetrieved))
  // }

  // test("prepared Mdoc invoked only once per dependency set") { (res, log) =>
  //   val content1 =
  //     """
  //   |hello!
  //   |
  //   |```scala mdoc
  //   |println("tut")
  //   |```""".stripMargin

  //   val content2 =
  //     """
  //   |hello!
  //   |
  //   |```scala mdoc
  //   |cats.effect.IO(println("tut")).unsafeRunSync()
  //   |```""".stripMargin

  //   val zeroDepContent = List.fill(5)(os.temp(content1, suffix = ".zero-dep.md"))
  //   val ceDepContent   = List.fill(5)(os.temp(content2, suffix = "cats-effect..md"))

  //   val preparedZeroDep = zeroDepContent.map { path =>
  //     path -> MdocFile(path, MdocConfiguration.default)
  //   }

  //   val preparedCEDep = ceDepContent.map { path =>
  //     path -> MdocFile(
  //       path,
  //       MdocConfiguration.default.copy(extraDependencies = Set("org.typelevel::cats-effect:2.3.0"))
  //     )
  //   }

  //   for {
  //     logs <- Ref.of[IO, Chain[String]](Chain.empty[String])

  //     logger = new Logger(s => effectCompat.sync(logs.update(_ ++ Chain(s)) *> log.info(s.replace("\n", "  "))))
  //     mdoc   = new Mdoc(logger = logger)

  //     prepared = mdoc.prepare(preparedZeroDep ++ preparedCEDep)

  //     resultFirst <- (
  //         res.block(prepared.get(zeroDepContent.head)).map(os.read),
  //         res.block(prepared.get(ceDepContent.head)).map(os.read)
  //     ).parTupled

  //     firstRetrievedZeroDep = resultFirst._1
  //     firstRetrievedCEDep   = resultFirst._2

  //     allRetrievedZeroDep <- zeroDepContent.parTraverse(path => res.block(os.read(prepared.get(path)))).map(_.distinct)
  //     allRetrievedCEDep   <- ceDepContent.parTraverse(path => res.block(os.read(prepared.get(path)))).map(_.distinct)

  //     results <- logs.get.map(_.toList)
  //   } yield expect(results.count(_.contains("Compiling 5 files to")) == 2) and
  //     expect(results.count(_.contains("Compiling")) == 2) and
  //     expect(allRetrievedZeroDep == List(firstRetrievedZeroDep)) and
  //     expect(allRetrievedCEDep == List(firstRetrievedCEDep))
  // }

}
