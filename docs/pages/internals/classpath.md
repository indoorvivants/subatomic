---
title: Mdoc and classpath
in_navigation_bar: false
---

#### This is an in-depth explanation of what Subatomic does when added to your build and why it needs to do so. You don't need to know this in 90% of usage scenarios

When building a static site for a library there are several things
we want the site builder to take care of us:

1. Markdown documents with Scala examples should be compiled and verified, using the classpath of the library we're documenting

2. We want to be able to pass the version from the build into markdown
documents, so that our installation instructions don't get outdated

## Mdoc and classpath

Subatomic manually invokes [Mdoc](https://scalameta.org/mdoc/) as a separate Java process, so to make it aware of the classpath of the
project we're building, we need to configure the build properly.

Say we have a SBT build as such:

**build.sbt**
```scala
lazy val core = project.in(file("core")).settings(...)
lazy val api = project.in(file("api")).dependsOn(core)
```

And we want to create documentation site for it, where snippets use code from both `core` and `api`.

We need to

1. Create a new `docs` project:

    ```scala
    lazy val docs = project.in(file("docs")).dependsOn(core, api)
    ```

2. Enable subatomic SBT plugin:

   **project/plugins.sbt**
   ```scala
     addSbtPlugin("com.indoorvivants" % "sbt-plugin" % "@VERSION@")
   ```

   **build.sbt**
   ```scala
    lazy val docs = 
      project
        .in(file("docs"))
        .dependsOn(core, api)
        .enablePlugins(SubatomicPlugin)
        .settings(
          subatomicInheritClasspath := true, // default, can be omitted
          subatomicBuildersDependency := true, // default, can be omitted
          subatomicMdocVariables := Map("VERSION" -> version.value) // default, can be omitted
        )
   ```

Doing so leads to two things happening:

1. `docs` project now has dependency on `"com.indoorvivants" %% "subatomic-builders" % "@VERSION@"` - which brings all the core things Subatomic will need to build the site

2. A [managed resource file](https://www.scala-sbt.org/1.x/docs/Classpaths.html#Unmanaged+vs+managed) called **subatomic.properties** is created (and kept updated)

The **subatomic.properties** file can be accessed by Subatomic when building the site and in our example it will contain the following information:

```
classpath=...
variable.VERSION=...
```

I hope `variable.VERSION` does not need explanation, so let's see what will `classpath` contain:

* `docs` dependencies classpath (including Subatomic builders)
* `docs` compiled classes
* `core` dependencies classpath
* `api` dependencies classpath
* `core` compiled classes
* `api` compiled classes

This contains enough information to be passed to Mdoc so that it can
compile and run examples in the documentation.

Note, that because the classpath contains `docs` own classes, you can add
some class you need for documentation rendering (for example, subatomic itself uses several classes to render ANSI-coloured outputs), and use it from markdown documents.

For example:

**docs/src/main/scala/PrettyOutput.scala**
```scala mdoc
object Prettify {
  def apply(out: String) =
    s"<div class='pretty'>$out</div>"
}
```

And in your docs:

````markdown
Pretty-printing:

```scala mdoc:passthrough
val result = (1 to 25).mkString(",")

println(Prettify(result))
```
````
