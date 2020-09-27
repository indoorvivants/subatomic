# Subatomic

**WIP: the snarky README only reflects the negatives with no way of knowing how those negatives are actually positives or how they can be addressed**

Are you a Scala developer, writing a Scala blog or maintaining a Scala project?

If so, do you want a static stie generator that:

* Requires you to write a **lot** of Scala
* Doesn't have a built-in theme
* Doesn't come with a nifty SBT/Mill plgun
* Barely does anything
* Is not configured with JSON
* Doesn't use React
* Acts as a glorified file manager and [mdoc](https://scalameta.org/mdoc/) wrapper

Nothing above should excite you. To ruin the impression even further, please head over to [example site build](/example). It uses Markdown for content, Scala data types for

It is what it is.

Whatever gets you close enough to calling the function below is up to you:

```scala
object Site {
 def build[Content](destination: os.Path)(
      sitemap: Vector[(os.RelPath, Content)] // this is really the main type
  )(assembler: Function2[os.RelPath, Content, Iterable[SiteAsset]]) = {
      //...
  }
}
```

or any of its cousins like:

```scala
def build1[Content, A1](destination: os.Path)(
      sitemap: Vector[(os.RelPath, Content)], // this is really the main type
      a1: Function2[os.RelPath, Content, A1]
  )(assembler: Function3[os.RelPath, Content, A1, Iterable[SiteAsset]]) = {
```

Which can help you handle extra components that are global to the site but vary their state depending on the location of the website.

And `SiteAsset` is just:

```scala
sealed trait SiteAsset
case class Page(content: String)                    extends SiteAsset
case class CopyOf(source: os.Path)                  extends SiteAsset
case class CreatedFile(at: os.Path, to: os.RelPath) extends SiteAsset
```

Site map is all up to you, e.g.:

```scala

sealed trait Content
case class MarkdownPage(title: String, markdownFile: os.Path)

def Pages(SiteRoot: os.RelPath, ContentRoot: os.Path): Vector[(os.RelPath, Content)] =
    Vector(
        SiteRoot / "index.html" -> MarkdownPage(
            "Home",
            ContentRoot / "content" / "pages" / "index.md"
        ),
        SiteRoot / "cv.html" -> MarkdownPage(
            "CV",
            ContentRoot / "content" / "pages" / "cv.md"
        )
    )
```

There's `MdocProcessor` and `MdocJsProcessor` to build pages with statically compiled snippets for Scala and Scala.js with custom dependency support.
