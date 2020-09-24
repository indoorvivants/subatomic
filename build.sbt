scalaVersion := "2.13.3"

libraryDependencies ++= Seq(
    "io.get-coursier" %% "coursier" % "2.0.0-RC6-24",
    "com.vladsch.flexmark" % "flexmark-all" % "0.62.2",
    "com.lihaoyi" %% "scalatags" % "0.9.1",
    "com.lihaoyi" %% "ammonite-ops" % "2.2.0"
)

name := "subatomic"

organization := "com.indoorvivants"

