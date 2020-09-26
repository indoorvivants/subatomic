lazy val root =
  project.aggregate(core, example).settings(skip in publish := true)

lazy val core = project
  .in(file("core"))
  .settings(
    name := "subatomic",
    scalaVersion := "2.13.3",
    crossScalaVersions := Seq("2.13.3", "2.12.12"),
    libraryDependencies ++= Seq(
      "io.get-coursier"     %% "coursier"     % "2.0.0-RC6-24",
      "com.vladsch.flexmark" % "flexmark-all" % "0.62.2",
      "com.lihaoyi"         %% "ammonite-ops" % "2.2.0"
    ),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, major)) if major <= 12 =>
          Seq()
        case _ =>
          Seq(
            "org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0"
          )
      }
    }
  )

lazy val example = project
  .in(file("example"))
  .dependsOn(core)
  .settings(
    scalaVersion := "2.13.3",
    crossScalaVersions := Seq("2.13.3", "2.12.12"),
    skip in publish := true,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "scalatags" % "0.9.1",
      "com.lihaoyi" %% "fansi"     % "0.2.7"
    )
  )

inThisBuild(
  List(
    scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.4.0",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalafixScalaBinaryVersion := scalaBinaryVersion.value,
    organization := "com.indoorvivants",
    homepage := Some(url("https://github.com/indoorvivants/subatomic")),
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "keynmol",
        "Anton Sviridov",
        "keynmol@gmail.com",
        url("https://blog.indoorvivants.com")
      )
    )
  )
)

val scalafixRules = Seq(
  "OrganizeImports",
  "DisableSyntax",
  "LeakingImplicitClassVal",
  "ProcedureSyntax",
  "NoValInForComprehension"
).mkString(" ")

val CICommands = Seq(
  "clean",
  "compile",
  "test",
  "scalafmtCheckAll",
  s"scalafix --check $scalafixRules"
).mkString(";")

val PrepareCICommands = Seq(
  s"compile:scalafix --rules $scalafixRules",
  s"test:scalafix --rules $scalafixRules",
  "test:scalafmtAll",
  "compile:scalafmtAll",
  "scalafmtSbt"
).mkString(";")

addCommandAlias("ci", CICommands)

addCommandAlias("preCI", PrepareCICommands)
