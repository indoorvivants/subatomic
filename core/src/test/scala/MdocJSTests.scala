package subatomic

import scala.util.Try

import weaver.SimpleMutableIOSuite
import weaver.Expectations
import cats.effect.IO
import cats.effect.Blocker
import weaver.Log

object MdocJSTests extends SimpleMutableIOSuite {

  val HelloWorldPath = SiteRoot / "hello" / "world"

  def process(content: String, dependencies: Set[String] = Set.empty)(
      result: ScalaJSResult => Expectations
  )(implicit log: Log[IO]): IO[Expectations] = {
    val mdoc = new MdocJS(logger = new Logger(s => log.info(s.replace("\n", "  ")).unsafeRunSync()))

    val tmpFile = os.temp(content, suffix = ".md")

    Blocker[IO].use(bl => bl.delay(mdoc.process(os.pwd, tmpFile, dependencies))).map(result)
  }

  loggedTest("mdoc.js works") { implicit log =>
    val content =
      """
    |hello!
    |
    |```scala mdoc:js
    |org.scalajs.dom.window.setInterval(() => {
    |  node.innerHTML = new java.util.Date().toString
    |}, 1000)
    |```""".stripMargin

    process(content) { result =>
      expect.all(
        os.read(result.mdFile).contains("mdoc-html-run0"),
        os.read(result.mdjsFile).nonEmpty,
        os.read(result.mdocFile).nonEmpty
      )
    }
  }

  loggedTest("mdoc.js with dependencies works") { implicit log =>
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

    process(content, Set("com.raquo::laminar_sjs1:0.11.0")) { result =>
      expect.all(
        os.read(result.mdFile).contains("mdoc-html-run0"),
        os.read(result.mdjsFile).nonEmpty,
        os.read(result.mdocFile).nonEmpty
      )
    }
  }
}
