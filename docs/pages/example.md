We're going build a simple website with statically checked Scala code in Markdown

We will use

* [Ammonite](https://ammonite.io), for writing the entire site builder in a single Scala script
* [Mdoc](https://scalameta.org/mdoc) to compile code examples in markdown
* [ScalaTags](https://lihaoyi.com/scalatags) to generate HTML

ScalaTags is not mandated by Subatomic (neither is writing it using Ammonite), but if we're already writing everything in Scala, why not HTML, too :-)

This document is copying the example outlined in the [repo](https://github.com/indoorvivants/subatomic/tree/master/docs/example)

## Folder structure

Our example folder structure will look like this:

```bash
# static files
assets/
    bootstrap.css
    highlight.js
    hightlight-scala.js
    highlight-theme.css
    script.js
    styles.css
pages/
    index.md # will become index.html
    scala-usage.md # will become scala-usage.html
    scala-js-usage.md # will become scala-js-usage.html
build.sc # static site builder
```

## Goals

What do we want to achieve:

* All static assets copied into the built site, accessible via same relative paths
* `scala-usage.md` will contain some code built with [Cats-effect](https://typelevel.org/cats-effect/) library and we want the snippets to be compiled and executed
* `scala-js-usage.md` will contain some code built with [Laminar](https://laminar.dev) library for Scala.js and we want the snippets to become interactive
* We want to be able to deploy our site to a relative URL of our choosing

  This matters when you maintain several sites from the same domain, and want to, say, build a static site for your library at **https://indoorvivants.com/subatomic**, and want to maintain your abandoned blog at **https://indoorvivants.com/blog**

  All links should work as expected.

## Dependencies and imports

```scala
import $ivy.`com.indoorvivants::subatomic:0.0.2`
import $ivy.`com.lihaoyi::scalatags:0.9.1`
```

```scala mdoc
import com.indoorvivants.subatomic._
import ammonite.ops._
```

## Content

Let's create two folders:

* `pages` where we will store our markdown pages
* `assets` where we will store our static files, like CSS and JS files

We're going to reperesent our content model very simply:

```scala mdoc
sealed trait Content
case class ScalaPage(
    title: String,
    path: os.Path,
    dependencies: Set[String]
) extends Content

case class ScalaJSPage(
    title: String,
    path: os.Path,
    dependencies: List[String]
) extends Content

case class StaticFile(path: os.Path)                  extends Content
case class MarkdownPage(title: String, path: os.Path) extends Content
```

* `StaticFile` is just that - a file copied without changes from `path`

* `MarkdownPage` - a page with no Scala snippets that we just want to render as HTML

* `ScalaPage` - a page that will be compiled using Mdoc (potentially with a list of dependencies)

* `ScalaJSPage` - same as `ScalaPage`, but will be compiled into Scala.js (JavaScript) and examples will be embedded.

Let's define our site map, by passing a version of subatomic itself:

```scala mdoc
object Content {
  def apply(root: os.Path) =
    Assets(root) ++ Pages(root)

  def Assets(root: os.Path): Vector[(SitePath, Content)] = {
    // recursively discovering all files in assets folder
    os.walk(root / "assets").filter(_.toIO.isFile()).map { path =>
      // make sure relative path on site matches relative path
      // in assets folder
      SiteRoot / path.relativeTo(root) -> StaticFile(path)
    }
  }.toVector

  def Pages(root: os.Path): Vector[(SitePath, Content)] = {
    Vector(
      SiteRoot / "index.html" -> MarkdownPage(
        "Home",
        root / "pages" / "index.md"
      ),
      SiteRoot / "scala-usage.html" -> ScalaPage(
        "Scala usage",
        root / "pages" / "scala-usage.md",
        Set("org.typelevel::cats-effect:2.2.0")
      ),
      SiteRoot / "scala-js-usage.html" -> ScalaJSPage(
        "Scala.js usage",
        root / "pages" / "scala-js-usage.md",
        List("com.raquo::laminar_sjs1:0.11.0")
      )
    )
  }
}
```

* For assets we just recursively walk over the `assets` folder matching the relative path in our sitemap
* We define our Markdown and Scala pages as separate objects


## Templates

Let's create a simple template with Bootstrap styles and Highlight.js dark theme for syntax highlighting:

```scala mdoc
class Template(linker: Linker) {
  import scalatags.Text.all._
  import scalatags.Text.TypedTag

  def RawHTML(rawHtml: String) = div(raw(rawHtml))

  def main(title: String, content: String): String =
    main(title, RawHTML(content))

  def main(title: String, content: TypedTag[_]): String = {
    html(
      head(
        scalatags.Text.tags2.title(title),
        link(
          rel := "stylesheet",
          href := linker.rooted(_ / "assets" / "highlight-theme.css")
        ),
        link(
          rel := "stylesheet",
          href := linker.rooted(_ / "assets" / "bootstrap.css")
        ),
        script(src := linker.rooted(_ / "assets" / "highlight.js")),
        script(src := linker.rooted(_ / "assets" / "highlight-scala.js")),
        script(src := linker.rooted(_ / "assets" / "script.js"))
      ),
      body(
        div(
          cls := "container",
          div(
            cls := "row",
            div(
              cls := "col-9",
              h1(title),
              content
            )
          )
        )
      )
    ).render
  }
}
```

### Assembling

As subatomic goes through the site map, for each location we need to output a sequence (potentially empty) of site assets:

```scala
sealed trait SiteAsset
case class Page(content: String)                      extends SiteAsset
case class CopyOf(source: os.Path)                    extends SiteAsset
case class CreatedFile(source: os.Path, to: SitePath) extends SiteAsset
```

* `Page` represents content copied into the location verbatim
* `CopyOf` copies a file from `source` into the location on the website
* `CreatedFile` is useful when the processing of a page produces extra resources.

    For example, if you're writing Scala.js documents, mdoc will produce 3 files:

    * `mdoc.js` which invokes the JavaScript snippets in your compiled document
    * `<filename>.js` which contains the code for each of the snippets that were compiled to JavaScript
    * `<filename>.md` resulting markdown file that references those files


Let's write a function that will assemble our site.

The function is fairly long but it's peppered with comments to help
understanding.

```scala mdoc
def createSite(
    destination: os.Path,
    contentRoot: os.Path = os.pwd,
    siteRoot: SitePath
) = {
  // creating a full site map
  val raw = Content(contentRoot)

  // shift all the content to match the site prefix (siteRoot)
  val content = raw.map {
    case (rawLocation, content) =>
      rawLocation.prepend(siteRoot) -> content
  }

  // helper to resolve links to their correct
  // values with regard to site root
  val linker = new Linker(raw, siteRoot)

  // built-in Mdoc interface
  val mdoc   = new MdocProcessor()
  val mdocJs = new MdocJsProcessor

  val template = new Template(linker)

  // wrapper around flexmark
  val markdown = Markdown(
    // optional:
    //   relativizes all  links in markdown
    //   relative to the path (in this case siteRoot)
    RelativizeLinksExtension(siteRoot.toRelPath)
  )

  Site.build(destination)(content) {
    // handling markdown pages
    case (path, MarkdownPage(title, markdownFile)) =>
      Some(
        Page(
          template.main(title, markdown.renderToString(markdownFile))
        )
      )

    // Processing Scala markdown pages with mdoc
    case (_, ScalaPage(title, mdFile, deps)) =>
      val processed = mdoc.process(mdFile, deps)
      Some(
        Page(
          template.main(title, markdown.renderToString(processed))
        )
      )

    // handling static assets
    case (_, sf: StaticFile) =>
      Some(CopyOf(sf.path))

    // Mdoc for Scala.js returns 3 files
    case (sitePath, ScalaJSPage(title, mdFile, deps)) =>
      val result = mdocJs.process(pwd, mdFile, deps)

      List(
        Page(
          template.main(title, markdown.renderToString(result.mdFile))
        ),
        CreatedFile(result.mdjsFile, sitePath.up / result.mdjsFile.last),
        CreatedFile(result.mdocFile, sitePath.up / "mdoc.js")
      )
  }
}
```

And this is it. Now we can call this function and it will render the full site at the
destination:

```scala mdoc
// using temporary folder as destination
createSite(
  destination = os.temp.dir(),
  contentRoot = os.pwd / "docs" / "example",
  siteRoot    = SiteRoot / "subatomic-example"
)
```
