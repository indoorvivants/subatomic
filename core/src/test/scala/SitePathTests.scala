package subatomic

import scala.util.Try

import weaver.SimpleMutableIOSuite

object SitePathTests extends SimpleMutableIOSuite {

  val HelloWorldPath = SiteRoot / "hello" / "world"

  pureTest("render") {
    expect.all(
      SiteRoot.toString() == "/",
      HelloWorldPath.toString == "/hello/world"
    )
  }

  pureTest("up: on root") {
    expect(Try(SiteRoot.up).failed.get.isInstanceOf[IllegalStateException])
  }

  pureTest("up: on nested") {
    expect.all(
      (SiteRoot / "hello").up == SiteRoot,
      HelloWorldPath.up.up == SiteRoot,
      HelloWorldPath.up == SiteRoot / "hello"
    )
  }

  pureTest("prepend: to root") {
    expect.all(
      SiteRoot.prepend(SiteRoot) == SiteRoot,
      SiteRoot.prepend(SiteRoot / "hello") == SiteRoot / "hello"
    )
  }

  pureTest("prepend: to nested") {
    expect.all(
      (SiteRoot / "world")
        .prepend(SiteRoot / "hello") == HelloWorldPath
    )
  }

  pureTest("prepend: to RelPath") {
    expect.all(
      SiteRoot.prepend(os.RelPath("hello/world")) == HelloWorldPath,
      (SiteRoot / "world").prepend(os.RelPath("hello")) == HelloWorldPath
    )
  }

  pureTest("fromRelPath") {
    expect.all(
      SitePath.fromRelPath(os.RelPath("hello/world")) == HelloWorldPath,
      SitePath.fromRelPath(os.RelPath("")) == SiteRoot,
      SitePath.fromRelPath(os.RelPath("hello")) == SiteRoot / "hello"
    )
  }

  pureTest("toRelPath") {
    expect.all(
      SiteRoot.toRelPath == os.RelPath(""),
      HelloWorldPath.toRelPath == os.RelPath("hello/world")
    )
  }
}
