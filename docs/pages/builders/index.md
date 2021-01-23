---
title: Builders
topnav: true
scala-mdoc: true
---


## Builders

Common scenarios (such as `LibrarySite` and `Blog`) are provided, which wrap raw APIs.

For example, here's the entire code for building Subatomic's own website as a single [Ammonite](https://ammonite.io) script:

**subatomic-site.sc**

```scala
import $ivy.`com.indoorvivants::subatomic-builders:@VERSION@`

import subatomic.builders.librarysite._
import subatomic.builders._

@main
def main(args: String*) = Docs.main(args)

object Docs extends LibrarySite.App {
  override def extra(site: Site[LibrarySite.Doc]) = {
    site
      .addCopyOf(SiteRoot / "CNAME", os.pwd / "docs" / "assets" / "CNAME")
  }

  def config =
    LibrarySite(
      name = "Subatomic",
      contentRoot = os.pwd / "docs" / "pages" / "internals",
      assetsRoot = Some(os.pwd / "docs" / "assets"),
      copyright = Some("© 2020 Anton Sviridov"),
      githubUrl = Some("https://github.com/indoorvivants/subatomic"),
      highlightJS = HighlightJS.default.copy(
        languages = List("scala"),
        theme = "monokai-sublime"
      )
    )
}
```

All builders provide some useful CLI flags:

```scala mdoc:passthrough
println("```text")
println(subatomic.builders.cli.command.showHelp)
println("```")
```

And running the script will provide a detailed overview of 
the static files being created:

```bash
$ amm subatomic-site.sc build --disable-mdoc
```

```scala mdoc:passthrough
val result = subatomic.docs.RunSite(
  subatomic.docs.Docs, List("--disable-mdoc")
)

println(subatomic.docs.Terminal.show(result))
```
