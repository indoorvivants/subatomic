This is an example of compiling Scala.js to JavaScript and running in interactively in the browser:


```scala mdoc
val x = println("Hello")
```


```scala mdoc:js
import com.raquo.laminar.api.L._

val diffBus = new EventBus[Int]

val $count: Signal[Int] = diffBus.events.foldLeft(initial = 0)(_ + _)

val app = div(
  h1("Let's count!"),
  h3(
    child.int <-- $count
  ),
  " ",
  button(
    "–",
    onClick.mapTo(-1) --> diffBus
  ),
  " ",
  button(
    "+",
    onClick.mapTo(+1) --> diffBus
  )
)

render(node, app)
```

[Go back](/index.html)
