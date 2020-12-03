package subatomic

import com.vladsch.flexmark.util.misc.Extension
import utest._

object HelloTests extends TestSuite {
  val tests = Tests {
    test("RelativizeLinksExtension") {
      val ext = RelativizeLinksExtension(SiteRoot / "test")

      test("relative links") {
        "[Hello World!](hello/world)".processedWith(ext) { result =>
          assert(
            result == """<p><a href="/test/hello/world">Hello World!</a></p>"""
          )
        }
      }

      test("absolute links") {
        "[Hello World!](/hello/world)".processedWith(ext) { result =>
          assert(
            result == """<p><a href="/test/hello/world">Hello World!</a></p>"""
          )
        }
      }

      test("image relative links") {
        "![Hello World!](hello/world)".processedWith(ext) { result =>
          assert(
            result == """<p><img src="/test/hello/world" alt="Hello World!" /></p>"""
          )
        }
      }

      test("image absolute links") {
        "![Hello World!](/hello/world)".processedWith(ext) { result =>
          assert(
            result == """<p><img src="/test/hello/world" alt="Hello World!" /></p>"""
          )
        }
      }
    }
  }

  implicit class StringOps(s: String) {
    def processedWith(l: Extension*)(htmlTest: String => Unit) = {
      val md = new Markdown(l.toList)

      htmlTest(md.renderToString(s).trim)
    }
  }
}
