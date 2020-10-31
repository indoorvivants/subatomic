package com.indoorvivants.subatomic

import utest._

object SitePathTests extends TestSuite {
  val tests = Tests {

    val HelloWorldPath = SiteRoot / "hello" / "world"

    test("SitePath") {
      test("render") {
        assert(
          SiteRoot.toString() == "/",
          HelloWorldPath.toString == "/hello/world"
        )
      }

      test("up") {
        test("root") {
          intercept[IllegalStateException] { val _ = SiteRoot.up }
        }
        test("nested") {
          assert(
            (SiteRoot / "hello").up == SiteRoot,
            HelloWorldPath.up.up == SiteRoot,
            HelloWorldPath.up == SiteRoot / "hello"
          )
        }
      }

      test("prepend") {
        test("root") {
          assert(
            SiteRoot.prepend(SiteRoot) == SiteRoot,
            SiteRoot.prepend(SiteRoot / "hello") == SiteRoot / "hello"
          )
        }

        test("nested") {
          assert(
            (SiteRoot / "world")
              .prepend(SiteRoot / "hello") == HelloWorldPath
          )
        }

        test("RelPath") {
          assert(
            SiteRoot.prepend(os.RelPath("hello/world")) == HelloWorldPath,
            (SiteRoot / "world").prepend(os.RelPath("hello")) == HelloWorldPath
          )
        }
      }

      test("fromRelPath") {
        assert(
          SitePath.fromRelPath(os.RelPath("hello/world")) == HelloWorldPath,
          SitePath.fromRelPath(os.RelPath("")) == SiteRoot,
          SitePath.fromRelPath(os.RelPath("hello")) == SiteRoot / "hello"
        )
      }

      test("toRelPath") {
        assert(
          SiteRoot.toRelPath == os.RelPath(""),
          HelloWorldPath.toRelPath == os.RelPath("hello/world")
        )
      }
    }
  }
}
