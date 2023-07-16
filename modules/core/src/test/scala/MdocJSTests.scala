package subatomic

import weaver.Expectations
import cats.effect.IO
import weaver._
import cats.effect.Blocker
import cats.effect.Resource

object MdocJSTests extends IOSuite {
  override type Res = Processor
  override def sharedResource: Resource[IO, Res] =
    Blocker[IO].map(new Processor(_))
  override def maxParallelism: Int =
    sys.env.get("CI").map(_ => 1).getOrElse(100)

  val HelloWorldPath = SiteRoot / "hello" / "world"

  class Processor(
      blocker: Blocker
  ) {

    def process(
        content: String,
        dependencies: Set[String] = Set.empty,
        log: Log[IO]
    )(
        result: ScalaJSResult => Expectations
    ): IO[Expectations] = {
      val logger = new Logger(s =>
        log.info(s.replace("\n", "  ")).unsafeRunSync()
      )

      val config =
        MdocConfiguration.default.copy(
          scalajsConfig = Some(ScalaJSConfig.default),
          extraDependencies = dependencies
        )
      val mdoc = new MdocJS(config, logger)

      val tmpFile = os.temp(content, suffix = ".md")

      blocker
        .blockOn(IO(mdoc.processAll(os.pwd, Seq(tmpFile))))
        .map(_.head._2)
        .map(result)
    }
  }

  def read(p: os.Path) = os.read(p)

  test("mdoc.js works") { (res, log) =>
    val content =
      """
    |hello!
    |
    |```scala mdoc:js
    |org.scalajs.dom.window.setInterval(() => {
    |  node.innerHTML = new java.util.Date().toString
    |}, 1000)
    |```""".stripMargin

    res.process(content, log = log) { result =>
      expect.all(
        read(result.mdFile).contains("mdoc-html-run0"),
        read(result.mdjsFile).nonEmpty,
        read(result.mdocFile).nonEmpty
      )
    }
  }

  test("mdoc.js with dependencies works") { (res, log) =>
    val content =
      """
    |hello!
    |
    |```scala mdoc:js
    |import com.raquo.laminar.api.L._
    |
    |val nameVar = Var(initial = "world")
    |
    |val rootElement = div(
    |  span(
    |    "Hello, ",
    |    child.text <-- nameVar.signal.map(_.toUpperCase)
    |  )
    |)
    |
    |render(node, rootElement)
    |```""".stripMargin

    res.process(content, Set("com.raquo::laminar_sjs1:16.0.0"), log = log) {
      result =>
        expect.all(
          read(result.mdFile).contains("mdoc-html-run0"),
          read(result.mdjsFile).nonEmpty,
          read(result.mdocFile).nonEmpty
        )
    }
  }
}
