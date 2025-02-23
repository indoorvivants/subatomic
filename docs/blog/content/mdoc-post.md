---
title: Mdoc post
date: 2022-03-14
tags: subatomic,blog,development,css
description: Where we pad our blogpost with enough content for it to be a feasible test of blog theme
mdoc: true
mdoc-scala: 3.3.5
mdoc-js: true
mdoc-dependencies: com.raquo::laminar_sjs1:17.2.0
--- 

```scala mdoc
println(1 + 2)
given Int = 25

val x: Int ?=> Unit = println(summon[Int])
```


```scala mdoc:js
org.scalajs.dom.window.setInterval(() => {
  node.innerHTML = new java.util.Date().toString
}, 1000)
```

```scala mdoc:js
import com.raquo.laminar.api.L._

val nameVar = Var(initial = "world")

val rootElement = div(
  span(
    "Hello, ",
    child.text <-- nameVar.signal.map(_.toUpperCase)
  )
)

render(node, rootElement)
```
