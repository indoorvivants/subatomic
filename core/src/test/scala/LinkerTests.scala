package com.indoorvivants.subatomic

import utest._

object LinkerTests extends TestSuite {
  val tests = Tests {

    val content = Vector(
      SiteRoot                     -> "indexpage!",
      SiteRoot / "hello"           -> "hellopage",
      SiteRoot / "hello" / "world" -> "worldpage!"
    )

    val linker = new Linker(content, SiteRoot / "test")

    test("Linker") {
      test("rooted") {

        assert(
          linker.rooted(_ / "hello") == "/test/hello",
          linker.rooted(_ / "hello" / "world") == "/test/hello/world",
          linker.rooted(identity) == "/test",
          linker.root == "/test"
        )
      }

      test("missing") {
        intercept[IllegalArgumentException] {
          val _ = linker.rooted(_ / "I-dont-exist")
        }
      }
    }
  }
}
