---
title: Example from scratch
scala-mdoc: true
---

We're going build a simple website with statically checked Scala code in Markdown.

Tools we will use:

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
    index.md # will become index.html, not processed with mdoc
    scala-usage.md # will become scala-usage.html, processed with mdoc
    scala-js-usage.md # will become scala-js-usage.html, processed with mdoc
build.sc # static site builder
```

## Goals

What do we want to achieve:

* All static assets copied into the built site, accessible via same relative paths
* `scala-usage.md` will contain some code built with [Cats-effect](https://typelevel.org/cats-effect/) library and we want the snippets to be compiled and executed
* `scala-js-usage.md` will contain some code built with [Laminar](https://laminar.dev) library for Scala.js and we want the snippets to become interactive
* We want to be able to deploy our site to a relative URL of our choosing

  This matters when you maintain several sites from the same domain, and want to, say, build a static site for your library at **https://indoorvivants.com/subatomic**, 
  and want to maintain your abandoned blog at **https://indoorvivants.com/blog**

  All links should work as expected.

## Dependencies and imports

```scala
import $ivy.`com.indoorvivants::subatomic-core:@VERSION@`
import $ivy.`com.lihaoyi::scalatags:0.9.1`
```

```scala mdoc
import subatomic._
```

## Defining content models

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

case class MarkdownPage(title: String, path: os.Path) extends Content
```

* `MarkdownPage` - a page with no Scala snippets that we just want to render as HTML

* `ScalaPage` - a page that will be compiled using Mdoc (potentially with a list of dependencies)

* `ScalaJSPage` - same as `ScalaPage`, but will be compiled into Scala.js (JavaScript) and examples will be embedded.

Let's define our site map, by passing a version of subatomic itself:

```scala mdoc
object Content {
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
          href := linker.unsafe(_ / "assets" / "highlight-theme.css")
        ),
        link(
          rel := "stylesheet",
          href := linker.unsafe(_ / "assets" / "bootstrap.css")
        ),
        script(src := linker.unsafe(_ / "assets" / "highlight.js")),
        script(src := linker.unsafe(_ / "assets" / "highlight-scala.js")),
        script(src := linker.unsafe(_ / "assets" / "script.js"))
      ),
      body(
        div(
          cls := "container",
          div(
            cls := "row",
            div(
              cls := "col-9",
              h1(title),
              content // this is what our pages will be rendered as
            )
          )
        )
      )
    ).render
  }
}
```

* We use `Linker` interface to resolve links on the site

* We define a helper method `RawHTML` which takes a `String` - markdown processor
will produce resulting HTML as a String.


## Configuring the site

Take a look at the whole function, it's excessively commented:

```scala mdoc:silent
def createSite(
    contentRoot: os.Path = os.pwd,
    siteRoot: SitePath
): Site[Content] = {
  // creating a full site map
  val raw = Content.Pages(contentRoot)

  // shift all the content to match the site prefix (siteRoot)
  val content = raw.map {
    case (rawLocation, content) =>
      rawLocation.prepend(siteRoot) -> content
  }

  // helper to resolve links to their correct
  // values with regard to site root
  val linker = new Linker(raw, siteRoot)
  
  val template = new Template(linker)

   // wrapper around flexmark
  val markdown = Markdown(
    // optional:
    //   relativizes all  links in markdown
    //   relative to the path (in this case siteRoot)
    RelativizeLinksExtension(siteRoot.toRelPath)
  )

  val scalaPageProcessor =
    MdocProcessor.create[ScalaPage]() {
      case ScalaPage(_, markdown, deps) => MdocFile(markdown, deps.toSet)
    }

  val scalaJSPageProcessor =
    MdocJSProcessor.create[ScalaJSPage]() {
      case ScalaJSPage(_, markdown, deps) => MdocFile(markdown, deps.toSet)
    }
  
  def renderMarkdownFile(title: String, filepath: os.Path): Page = {
    val renderedHtml = markdown.renderToString(filepath)
    val fullPageHtml = template.main(title, renderedHtml)

    Page(fullPageHtml)
  }

  val scalaPageRenderer: Processor[ScalaPage, SiteAsset] = scalaPageProcessor
    // after mdoc processes the page, we want to convert markdown to HTML 
    .map(result => renderMarkdownFile(result.original.title, result.resultFile))

  // Pages with Scala.js examples are a bit special
  // Mdoc produces 3 files associated with each markdown file you give it
  // We need to be very careful about placing the JS files 
  // in the correct locations (because they're already references in generated
  // markdown)
  def scalaJSPageRenderer(mainDocPath: SitePath): Processor[ScalaJSPage, Map[SitePath, SiteAsset]] =
    scalaJSPageProcessor.map { result =>
      Map(
        // Markdown file to become the page itself
        mainDocPath -> renderMarkdownFile(result.original.title, result.markdownFile),
        // Implementations of functions associated with each snippet
        mainDocPath.up / result.jsSnippetsFile.last -> CopyOf(result.jsSnippetsFile),
        // general file that triggers the snippets' code when page is loaded
        mainDocPath.up / "mdoc.js" -> CopyOf(result.jsInitialisationFile)
      )
    }

  Site
    // we're just adding content to call populate later, 
    // at the moment the site is empty
    .init(content) 
    // populate the site by going over every piece of content
    // and applying a function that can add pages to the site
    .populate {
      case (site, content) =>
        content match {
          case (sitePath, doc: ScalaPage) =>
            // we're telling subatomic to use scalaPageRenderer
            // to process this piece of content
            // when the site is built
            site.addProcessed(sitePath, scalaPageRenderer, doc)

          case (sitePath, doc: MarkdownPage) =>
            // we don't need to do any special processing on
            // regular markdown pages (calling mdoc is expensive)
            // so we can just use addPage
            site.add(sitePath, renderMarkdownFile(doc.title, doc.path))

          case (sitePath, doc: ScalaJSPage) =>
            // we're using a special 
            site.addProcessed(scalaJSPageRenderer(sitePath), doc)
        }
    }
    // copy all static files to be served at /assets/ on our site
    .copyAll(contentRoot / "assets", SiteRoot / "assets")
}

val site = createSite(
  contentRoot = os.pwd / "docs" / "example",
  siteRoot    = SiteRoot / "example" / "subatomic-example"
)
```


### Building the site 

```scala mdoc:compile-only
site.buildAt(
  destination = os.temp.dir(), 
  overwrite = true
)
```

```scala mdoc:passthrough
import subatomic.docs._

println(Terminal.show(RunSite(site, os.temp.dir())))
```
