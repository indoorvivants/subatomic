lazy val core = projectMatrix
  .in(file("core"))
  .settings(
    name := "subatomic",
    libraryDependencies ++= Seq(
      "io.get-coursier"        %% "coursier"                % "2.0.0-RC6-24",
      "com.vladsch.flexmark"    % "flexmark-all"            % "0.62.2",
      "com.lihaoyi"            %% "ammonite-ops"            % "2.2.0",
      "io.lemonlabs"           %% "scala-uri"               % "3.0.0",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.2.0",
      "com.lihaoyi"            %% "utest"                   % "0.7.2" % Test
    ),
    testFrameworks += new TestFramework("utest.runner.Framework"),
    scalacOptions.in(Test) ~= filterConsoleScalacOptions
  )
  .jvmPlatform(
    scalaVersions = Seq("2.13.3", "2.12.12")
  )
  .settings(sharedSettings)

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
      import scala.collection.mutable
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
  .settings(sharedSettings)

lazy val sharedSettings = {
  import java.io.PrintStream
  import sbt.internal.LogManager

  Seq(
    logManager := LogManager.defaultManager(
      ConsoleOut.printStreamOut(new PrintStream(System.out) {
        val project = thisProjectRef.value.project
        override def println(str: String): Unit = {
          val (lvl, msg) = str.span(_ != ']')
          super.println(s"$lvl] [$project$msg")
        }
      })
    ),
    fork in Test := false
  )
}

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
  "headerCheck"
).mkString(";")

val PrepareCICommands = Seq(
  s"core/compile:scalafix --rules $scalafixRules",
  s"core/test:scalafix --rules $scalafixRules",
  "core/test:scalafmtAll",
  "core/compile:scalafmtAll",
  "scalafmtSbt",
  "headerCreate"
).mkString(";")

addCommandAlias("ci", CICommands)

addCommandAlias("preCI", PrepareCICommands)
