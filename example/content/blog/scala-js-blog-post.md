
## Scala.js Example

This example uses Scala.js and Mdoc to render interactive snippets.

```scala mdoc:js
org.scalajs.dom.window.setInterval(() => {
  node.innerHTML = new java.util.Date().toString
}, 1000)
```
