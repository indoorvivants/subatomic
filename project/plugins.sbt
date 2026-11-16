{
  if (sys.env.contains("DEV"))
    Seq(addSbtPlugin("org.typelevel" % "sbt-tpolecat" % "0.5.7"))
  else Seq.empty
}

// addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.2.16")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")

addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.12.1")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.10")

addSbtPlugin("com.github.sbt" % "sbt-header" % "5.11.0")

// sbt-projectmatrix is built into sbt 2

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.2")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.23.0")

addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.13.0")

addSbtPlugin("com.indoorvivants" % "sbt-commandmatrix" % "0.1.0")

// addSbtPlugin("com.indoorvivants" % "sbt-doc-view" % "0.2.0+4-959cb301-SNAPSHOT")
