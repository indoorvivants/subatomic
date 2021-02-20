package subatomic
package builders

import weaver._
import cats.effect._
import weaver.{Log => WeaverLog}

object LibrarySiteTest extends SimpleIOSuite with BuildersHelpers {
  import subatomic.builders.librarysite.LibrarySite._
  import subatomic.builders.librarysite.LibrarySite

  pureTest("discovery: attributes and paths") {
    val basic = prepareContent(
      SiteRoot / "base.md"           -> page(Map("title" -> "base"), "hello!"),
      SiteRoot / "test.md"           -> page(Map("title" -> "Test"), "testing!"),
      SiteRoot / "check" / "test.md" -> page(Map("title" -> "check"), "checking!")
    )

    val conf = LibrarySite(contentRoot = basic.root, name = "hello")

    val content = LibrarySite.discoverContent(conf).toMap

    expect.all(
      content.exists(_._2.title == "base"),
      content.exists(_._2.title == "Test"),
      content.exists(_._2.title == "check"),
      content.get(SiteRoot / "base" / "index.html").exists(_.path == basic.files(SiteRoot / "base.md")),
      content.get(SiteRoot / "test" / "index.html").exists(_.path == basic.files(SiteRoot / "test.md")),
      content
        .get(SiteRoot / "check" / "test" / "index.html")
        .exists(_.path == basic.files(SiteRoot / "check" / "test.md"))
    )
  }

  pureTest("discovery: Mdoc config") {

    val regularPage = page(Map("title" -> "base"), "test")

    val testDependency = "org.typelevel::cats:2.3.1"
    val mdocPage = page(
      Map("title" -> "mdoc", "mdoc" -> "true", "mdoc-dependencies" -> testDependency),
      "test mdoc"
    )

    val basic = prepareContent(
      SiteRoot / "base.md" -> regularPage,
      SiteRoot / "mdoc.md" -> mdocPage
    )

    val conf = LibrarySite(contentRoot = basic.root, name = "hello")

    val content = LibrarySite.discoverContent(conf).toMap

    expect.all(
      content.get(SiteRoot / "base" / "index.html").exists(_.mdocConfig.isEmpty),
      content
        .get(SiteRoot / "mdoc" / "index.html")
        .exists(_.mdocConfig.exists(_.extraDependencies.contains(testDependency)))
    )
  }
}

trait BuildersHelpers {

  import weaver._
  import cats.effect._
  import weaver.{Log => WeaverLog}

  class Check(blocker: Blocker)(implicit cs: ContextShift[IO]) {
    def check[C](site: Site[C])(f: os.Path => Expectations): IO[Expectations] = {
      blocker.blockOn(IO {
        val destination = os.temp.dir()

        site.buildAt(destination)

        f(destination)
      })
    }
  }

  def baseSite[Doc](content: Iterable[(SitePath, Doc)])(log: WeaverLog[IO]) =
    Site.init(content).changeLogger(s => log.info(s.replace("\n", "  ")).unsafeRunSync())

  case class PreparedContent(root: os.Path, files: Map[SitePath, os.Path])

  def prepareContent(sps: (SitePath, String)*): PreparedContent = {
    val root = os.temp.dir()
    val files = sps.map {
      case (rp, value) =>
        os.makeDir.all((root / rp.toRelPath) / os.up)
        os.write.over(root / rp.toRelPath, value)

        rp -> (root / rp.toRelPath)
    }

    PreparedContent(root, files.toMap)
  }

  def page(attrs: Map[String, String], content: String) = {
    val header = attrs.map { case (k, v) => s"$k: $v" }.mkString("---\n", "\n", "\n---\n")

    header + content
  }
}
