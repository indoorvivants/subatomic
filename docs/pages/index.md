---
id: home
title: Home
index: true
---

Subatomic is not a framework for _quickly_ building static websites.

It's a library with a lot of useful helpers that help you configure your site building process
with code.

* Structure of your content is up to you - use Scala's data structures, pattern matching, 
all the usual stuff

  ```scala
  sealed trait Content
  case class StaticPage(title: String, path: os.Path) extends Content
  case class MdocPage(path: os.Path, dependencies: Set[String]) extends Content
  ```

* No magic. You need to explicitly tell Subatomic where to put which content.

  The content model is very simple - Subatomic can copy files and it can write text to files. That's it.

Here's a super-short example:

```scala mdoc
import subatomic._

type Content = Either[String, Int]

def sitemap: Vector[(SitePath, Content)] = Vector(
  SiteRoot / "index.html"            -> Left("Hello world!"),
  SiteRoot / "the" / "answer.html"   -> Right(25)
)

Site
  .init(sitemap)
  .populate { case (site, content) => 
    content match {
      case (path, Left(string)) => 
        site.addPage(path, s"<html><body>String: $string</body></html>")
      case (path, Right(int)) => 
        site.addPage(path, s"<html><body>Integer: $int</body></html>")
    }
  }
  .buildAt(os.temp.dir())
```

For a much longer example (with Mdoc, link relativization, and Scala.js, take a look at the [Example](example.html))
