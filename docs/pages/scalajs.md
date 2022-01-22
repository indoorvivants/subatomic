---
id: scalajs
title: Scala.js support 
mdoc: true
mdoc-js: true
mdoc-dependencies: com.raquo::laminar_sjs1:0.14.2
---


Out of the box we support Mdoc's [ability](https://scalameta.org/mdoc/docs/js.html) to render Scala.js snippets

All you need to do is to have this setting turned on in the head of 
your Markdown document:

```
scala-mdoc-js: true
scala-mdoc-js-dependencies: com.raquo::laminar_sjs1:0.11.0
```

`scala-mdoc-js-dependencies` is optional, and can be used to add
dependencies that will be used to compile your snippets.

Dependencies apply on per-file basis only.


For example, here's a snippet from [Laminar's own website](http://laminar.dev/examples/time), used verbatim to render this page:

```scala mdoc:js
import com.raquo.laminar.api.L._
import org.scalajs.dom

val clickBus = new EventBus[Unit]

val $maybeAlert = EventStream.merge(
  clickBus.events.mapTo(Some(span("Just clicked!"))),
  clickBus.events.flatMap { _ =>
    EventStream.fromValue(None, emitOnce = true).delay(500)
  }
)

val app = div(
  button(onClick.mapTo(()) --> clickBus, "Click me"),
  child.maybe <-- $maybeAlert
)

render(node, app)
```
