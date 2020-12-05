package subatomic

import scala.util.Failure
import scala.util.Success
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
      linker.rooted(_ / "hello") == "/test/hello",
      linker.rooted(_ / "hello" / "world") == "/test/hello/world",
      linker.rooted(identity) == "/test",
      linker.root == "/test"
    )
  }

  pureTest("missing") {
    expect(
      Try(linker.rooted(_ / "I-dont-exist")).failed.get
        .isInstanceOf[IllegalArgumentException]
    )
  }
}
