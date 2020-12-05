package subatomic

import scala.util.Try

import weaver.SimpleMutableIOSuite

object SitePathTests extends SimpleMutableIOSuite {

  val HelloWorldPath = SiteRoot / "hello" / "world"

  test("render") {
    expect.all(
      SiteRoot.toString() == "/",
      HelloWorldPath.toString == "/hello/world"
    )
  }

  test("up: on root") {
    expect(Try(SiteRoot.up).failed.get.isInstanceOf[IllegalStateException])
  }

  test("up: on nested") {
    expect.all(
      (SiteRoot / "hello").up == SiteRoot,
      HelloWorldPath.up.up == SiteRoot,
      HelloWorldPath.up == SiteRoot / "hello"
    )
  }

  test("prepend: to root") {
    expect.all(
      SiteRoot.prepend(SiteRoot) == SiteRoot,
      SiteRoot.prepend(SiteRoot / "hello") == SiteRoot / "hello"
    )
  }

  test("prepend: to nested") {
    expect.all(
      (SiteRoot / "world")
        .prepend(SiteRoot / "hello") == HelloWorldPath
    )
  }

  test("prepend: to RelPath") {
    expect.all(
      SiteRoot.prepend(os.RelPath("hello/world")) == HelloWorldPath,
      (SiteRoot / "world").prepend(os.RelPath("hello")) == HelloWorldPath
    )
  }

  test("fromRelPath") {
    expect.all(
      SitePath.fromRelPath(os.RelPath("hello/world")) == HelloWorldPath,
      SitePath.fromRelPath(os.RelPath("")) == SiteRoot,
      SitePath.fromRelPath(os.RelPath("hello")) == SiteRoot / "hello"
    )
  }

  test("toRelPath") {
    expect.all(
      SiteRoot.toRelPath == os.RelPath(""),
      HelloWorldPath.toRelPath == os.RelPath("hello/world")
    )
  }
}
