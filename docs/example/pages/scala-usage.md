This is an example of compiling Markdown pages with Scala snippets
using custom dependencies

```scala mdoc
import cats.effect._

val print = IO(println("hello!"))

def prog = print *> print *> print

prog.unsafeRunSync()
```

[Go back](/index.html)
