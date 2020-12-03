Subatomic is not a framework for quickly building static websites.

It's more of a collection of wrappers around things commonly used for building websites for Scala blogs/library pages.

Additional goal was being able to use Mdoc's ability to render Scala.js snippets inline for interactive documentation.

At its core, Subatomic wants you to call this function:

```scala
object Site {
  def build[Content](destination: os.Path)(
        sitemap: Vector[(SitePath, Content)]
    )(assembler: (SitePath, Content) => Iterable[SiteAsset]]): Unit
}
```

Note that `Content` is a type parameter - you define what your content structure
looks like.

`SiteAsset` is very simple:

```scala
sealed trait SiteAsset
case class Page(content: String)                      extends SiteAsset
case class CopyOf(source: os.Path)                    extends SiteAsset
case class CreatedFile(source: os.Path, to: SitePath) extends SiteAsset
```

Apart from that - there's no built-in theme, there's no JSON configs, there's no React, there's... nothing, really.

Here's a super-short example:

```scala mdoc
import subatomic._

type Content = Either[String, Int]

def sitemap: Vector[(SitePath, Content)] = Vector(
  SiteRoot / "index.html"            -> Left("Hello world!"),
  SiteRoot / "the" / "answer.html"   -> Right(25)
)

Site.build(os.temp.dir())(sitemap) {
  case (_, Left(string)) =>
    Some(
      Page(s"<html><body>String: $string</body></html>")
    )

  case (_, Right(int)) =>
    Some(
      Page(s"<html><body>Integer: $int</body></html>")
    )
}
```

For a much longer example (with Mdoc, link relativization, and Scala.js, take a look at the [Example](example.html))
