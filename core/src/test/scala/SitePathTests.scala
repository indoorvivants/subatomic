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
      SiteRoot.prepend(relPath("hello/world")) == HelloWorldPath,
      (SiteRoot / "world").prepend(relPath("hello")) == HelloWorldPath
    )
  }

  pureTest("fromRelPath") {
    expect.all(
      SitePath.fromRelPath(relPath("hello/world")) == HelloWorldPath,
      SitePath.fromRelPath(relPath("")) == SiteRoot,
      SitePath.fromRelPath(relPath("hello")) == SiteRoot / "hello"
    )
  }

  def relPath(s: String) = os.RelPath(s)

  pureTest("toRelPath") {
    expect.all(
      SiteRoot.toRelPath == relPath(""),
      HelloWorldPath.toRelPath == relPath("hello/world")
    )
  }
}
