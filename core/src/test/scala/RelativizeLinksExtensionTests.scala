package subatomic

import com.vladsch.flexmark.util.misc.Extension
import weaver.Expectations
import weaver.SimpleMutableIOSuite
import weaver.SourceLocation

object RelativizeLinksExtensionTests extends SimpleMutableIOSuite {

  val ext = RelativizeLinksExtension(SiteRoot / "test")

  pureTest("relative links") {
    "[Hello World!](hello/world)".processedWith(ext) { result =>
      expect(
        result == """<p><a href="/test/hello/world">Hello World!</a></p>"""
      )
    }
  }

  pureTest("absolute links") {
    "[Hello World!](/hello/world)".processedWith(ext) { result =>
      expect(
        result == """<p><a href="/test/hello/world">Hello World!</a></p>"""
      )
    }
  }

  pureTest("image relative links") {
    "![Hello World!](hello/world)".processedWith(ext) { result =>
      expect(
        result == """<p><img src="/test/hello/world" alt="Hello World!" /></p>"""
      )
    }
  }

  pureTest("image absolute links") {
    "![Hello World!](/hello/world)".processedWith(ext) { result =>
      expect(
        result == """<p><img src="/test/hello/world" alt="Hello World!" /></p>"""
      )
    }
  }

  implicit class StringOps(s: String) {
    def processedWith(
        l: Extension*
    )(htmlTest: String => Expectations) = {
      val md = new Markdown(l.toList)

      htmlTest(md.renderToString(s).trim)
    }
  }
}
