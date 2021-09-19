package subatomic

import com.vladsch.flexmark.util.misc.Extension
import weaver.Expectations

object RelativizeLinksExtensionTests extends weaver.FunSuite {

  val ext     = RelativizeLinksExtension(SiteRoot / "test")
  val shifted = SiteRoot / "test"
  val root    = SiteRoot

  val expectations = List(
    ("hello/world", shifted, "/test/hello/world"),
    ("hello/world", root, "/hello/world"),
    ("/hello/world", shifted, "/test/hello/world"),
    ("/hello/world", root, "/hello/world"),
    ("#test-this", root, "#test-this"),
    ("#test-this", shifted, "#test-this"),
    ("hello/world#test-this", root, "/hello/world#test-this"),
    ("hello/world#test-this", shifted, "/test/hello/world#test-this")
  )

  expectations.foreach { case (sourceUrl, mode, expected) =>
    test(s"(link) When path is [$mode], $sourceUrl --> $expected") {
      s"[Hello World]($sourceUrl)".processedWith(RelativizeLinksExtension(mode)) { result =>
        expect.same(
          result,
          s"""<p><a href="$expected">Hello World</a></p>"""
        )
      }
    }
    test(s"(image link) When path is [$mode], $sourceUrl --> $expected") {
      s"![Hello World]($sourceUrl)".processedWith(RelativizeLinksExtension(mode)) { result =>
        expect.same(
          result,
          s"""<p><img src="$expected" alt="Hello World" /></p>"""
        )
      }
    }
  }

  private implicit class Ops(s: String) {
    def processedWith(
        l: Extension*
    )(htmlTest: String => Expectations) = {
      val md = new Markdown(l.toList)

      htmlTest(md.renderToString(s).trim)
    }
  }
}
