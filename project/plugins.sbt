{
  if (sys.env.contains("DEV"))
    Seq(addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.4.2"))
  else Seq.empty
}

/* resolvers += Resolver.sonatypeRepo("snapshots") */

addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.2.16")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")

addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.12")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.12.0")

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.9.0")

addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.10.0")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.14.0")

addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.17")

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")

addSbtPlugin("com.indoorvivants" % "sbt-commandmatrix" % "0.0.5")
