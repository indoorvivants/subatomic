import scala.collection.mutable
lazy val core = projectMatrix
  .in(file("core"))
  .settings(
    name := "subatomic",
    libraryDependencies ++= Seq(
      "io.get-coursier"        %% "coursier"                % "2.0.0-RC6-24",
      "com.vladsch.flexmark"    % "flexmark-all"            % "0.62.2",
      "com.lihaoyi"            %% "ammonite-ops"            % "2.2.0",
      "io.lemonlabs"           %% "scala-uri"               % "3.0.0",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.2.0"
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
  .jvmPlatform(
    scalaVersions = Seq("2.13.3", "2.12.12")
  )

val site = inputKey[Unit](
  "Generate subatomic site with version same as the current build"
)

lazy val docs = projectMatrix
  .in(file("docs"))
  .dependsOn(core)
  .jvmPlatform(scalaVersions = Seq("2.13.3"))
  .settings(
    skip in publish := true,
    unmanagedSourceDirectories in Compile +=
      (baseDirectory in ThisBuild).value / "docs",
    libraryDependencies += "com.lihaoyi"  %% "scalatags" % "0.9.1",
    libraryDependencies += "com.monovore" %% "decline"   % "1.3.0",
    site := Def.inputTaskDyn {
      val parsed = sbt.complete.DefaultParsers.spaceDelimited("<arg>").parsed
      val args = Iterator(
        parsed
      ).flatten.mkString(" ")

      Def.taskDyn {
        runMain.in(Compile).toTask(s" docs.Main $args")
      }
    }.evaluated,
    resourceGenerators in Compile += Def.task {
      val out =
        managedResourceDirectories
          .in(Compile)
          .value
          .head / "subatomic.properties"
      val props     = new java.util.Properties()
      val classpath = mutable.ListBuffer.empty[File]
      // Can't use fullClasspath.value because it introduces cyclic dependency between
      // compilation and resource generation.
      classpath ++= dependencyClasspath.in(Compile).value.iterator.map(_.data)
      classpath += classDirectory.in(Compile).value

      props.setProperty(
        "classpath",
        classpath.mkString(java.io.File.pathSeparator)
      )

      IO.write(props, "subatomic properties", out)

      List(out)
    }
  )

inThisBuild(
  List(
    scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.4.0",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalafixScalaBinaryVersion := scalaBinaryVersion.value,
    organization := "com.indoorvivants",
    organizationName := "Anton Sviridov",
    homepage := Some(url("https://github.com/indoorvivants/subatomic")),
    startYear := Some(2020),
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
  s"scalafix --check $scalafixRules",
  "missinglinkCheck",
  "headerCheck"
).mkString(";")

val PrepareCICommands = Seq(
  s"compile:scalafix --rules $scalafixRules",
  s"test:scalafix --rules $scalafixRules",
  "test:scalafmtAll",
  "compile:scalafmtAll",
  "scalafmtSbt",
  "missinglinkCheck",
  "headerCreate"
).mkString(";")

addCommandAlias("ci", CICommands)

addCommandAlias("preCI", PrepareCICommands)
