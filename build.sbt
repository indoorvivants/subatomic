lazy val root = project
  .aggregate(
    (Seq(docs, plugin) ++ core.projectRefs): _*
  )
  .settings(
    skip in publish := true
  )

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
    scalaVersions = Seq(Scala_213, Scala_212)
  )
  .settings(sharedSettings)
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings)

lazy val docsMatrix = projectMatrix
  .in(file("docs"))
  .withId("docs")
  .dependsOn(core, pluginMatrix)
  .jvmPlatform(scalaVersions = Seq(Scala_212))
  .enablePlugins(SubatomicPlugin)
  .settings(
    skip in publish := true,
    unmanagedSourceDirectories in Compile +=
      (baseDirectory in ThisBuild).value / "docs",
    libraryDependencies += "com.lihaoyi"  %% "scalatags" % "0.9.1",
    libraryDependencies += "com.monovore" %% "decline"   % "1.3.0",
    subatomicAddDependency := false,
    subatomicInheritClasspath := true
  )
  .settings(sharedSettings)
  .settings(buildInfoSettings)

lazy val docs = docsMatrix.jvm(Scala_212).project

lazy val pluginMatrix = projectMatrix
  .in(file("sbt-plugin"))
  .withId("plugin")
  .settings(
    sbtPlugin := true,
    sbtVersion in pluginCrossBuild := "1.4.4"
  )
  .jvmPlatform(scalaVersions = Seq(Scala_212))
  .settings(
    moduleName := "subatomic-plugin",
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  )
  .settings(
    publishLocal := publishLocal
      .dependsOn(
        publishLocal in core.jvm(Scala_212)
      )
      .value
  )
  .settings(
    resourceGenerators in Compile += Def.task {
      val out =
        managedResourceDirectories
          .in(Compile)
          .value
          .head / "subatomic-plugin.properties"

      val props = new java.util.Properties()

      props.setProperty("subatomic.version", version.value)

      IO.write(props, "subatomic plugin properties", out)

      List(out)
    }
  )
  .enablePlugins(ScriptedPlugin, SbtPlugin)

lazy val plugin = pluginMatrix.jvm(Scala_212).project

/**
  * Settings
  */

val Scala_213 = "2.13.3"
val Scala_212 = "2.12.12"

lazy val sharedSettings = {
  Seq(
    fork in Test := false
  )
}

lazy val buildInfoSettings = {
  Seq(
    buildInfoPackage := "subatomic.internal",
    buildInfoKeys := Seq[BuildInfoKey](
      version,
      scalaVersion,
      scalaBinaryVersion
    )
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
  "scripted",
  "scalafmtCheckAll",
  s"scalafix --check $scalafixRules",
  "headerCheck"
).mkString(";")

val PrepareCICommands = Seq(
  s"core/compile:scalafix --rules $scalafixRules",
  s"core/test:scalafix --rules $scalafixRules",
  s"plugin2_12/compile:scalafix --rules $scalafixRules",
  "core/test:scalafmtAll",
  "core/compile:scalafmtAll",
  "docs2_12/compile:scalafmtAll",
  "plugin2_12/compile:scalafmtAll",
  "scalafmtSbt",
  "headerCreate"
).mkString(";")

addCommandAlias("ci", CICommands)

addCommandAlias("preCI", PrepareCICommands)

addCommandAlias("buildSite", "docs2_12/run")
