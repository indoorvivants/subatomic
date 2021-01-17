---
title: Home
index: true
scala-mdoc: true
---

# Installation

## SBT

**project/plugins.sbt**
```scala
addSbtPlugin("com.indoorvivants" % "subatomic-plugin" % "@VERSION@")
```

**build.sbt**

```scala
lazy val docs = 
  project
    .in(file("docs"))
    .enablePlugins(SubatomicPlugin)
```

Now you can use one of the [builders](/builders) to build your site!

## Ammonite

Only reason to use SBT plugin is to handle classpath propagation from your build (see [internals](/internals/classpath) for explanation).

If you don't want that and want to control your mdoc dependencies
from within the documents, you could just use the builders API directly from Ammonite:


```scala
import $ivy.`com.indoorvivants::subatomic-builders:@VERSION@`

import subatomic.builders.librarysite._
import subatomic.builders._

@main
def main(args: String*) = Docs.main(args)

object Docs extends LibrarySite.App {
  def config =
    LibrarySite(
      name = "My library",
      contentRoot = os.pwd / "docs" / "pages",
      highlightJS = HighlightJS.default
    )
}
```
