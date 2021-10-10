package subatomic

import scala.util.Try

import weaver.SimpleMutableIOSuite

object LinkerTests extends SimpleMutableIOSuite {

  val content = Vector(
    SiteRoot                     -> "indexpage!",
    SiteRoot / "hello"           -> "hellopage",
    SiteRoot / "hello" / "world" -> "worldpage!"
  )

  val linker = new Linker(content, SiteRoot / "test")

  pureTest("rooted") {

    expect.all(
      linker.resolve(_ / "hello") == "/test/hello",
      linker.resolve(_ / "hello" / "world") == "/test/hello/world",
      linker.resolve(identity) == "/test",
      linker.root == "/test"
    )
  }

  pureTest("missing") {
    expect(
      Try(linker.resolve(_ / "I-dont-exist")).failed.get
        .isInstanceOf[IllegalArgumentException]
    )
  }
}
