---
title: Raw API
scala-mdoc: true
---

Under the hood, Subatomic's core idea is "it's all just moving files around" when it comes to building static sites.


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

```scala mdoc:silent
import subatomic._

type Content = Either[String, Int]

def sitemap: Vector[(SitePath, Content)] = Vector(
  SiteRoot / "index.html"            -> Left("Hello world!"),
  SiteRoot / "the" / "answer.html"   -> Right(25)
)

val site = Site
  .init(sitemap)
  .populate { case (site, content) => 
    content match {
      case (path, Left(string)) => 
        site.addPage(path, s"<html><body>String: $string</body></html>")
      case (path, Right(int)) => 
        site.addPage(path, s"<html><body>Integer: $int</body></html>")
    }
  }
```

And if we want to actually produce files, we can call `buildAt`:

```scala mdoc:compile-only
site.buildAt(os.temp.dir()) // build in a temp folder
```

```scala mdoc:passthrough
import subatomic.docs._

println(Terminal.show(RunSite(site, os.temp.dir())))
```
