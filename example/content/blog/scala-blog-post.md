# Scala example

```scala mdoc
import cats.effect._

val x = IO(println("hello"))

(x *> x *> x).unsafeRunSync()
```
