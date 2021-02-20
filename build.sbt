import BuildSettings._

scalaVersion := Scala_213

lazy val root = project
  .aggregate(
    Seq(
      plugin.projectRefs,
      core.projectRefs,
      builders.projectRefs,
      searchIndex.projectRefs,
      searchShared.projectRefs,
      searchFrontend.projectRefs,
      searchFrontendPack.projectRefs,
      searchRetrieve.projectRefs,
      searchCli.projectRefs
    ).flatten: _*
  )
  .settings(skipPublish)

val flexmarkModules = Seq(
  "",
  "-ext-yaml-front-matter",
  "-ext-anchorlink"
).map(mName => "com.vladsch.flexmark" % s"flexmark$mName" % "0.62.2")

lazy val core = projectMatrix
  .in(file("core"))
  .settings(
    name := "subatomic-core",
    libraryDependencies ++= Seq(
      "io.get-coursier"        %% "coursier"                % "2.0.0-RC6-24",
      "com.lihaoyi"            %% "os-lib"                  % "0.7.2",
      "io.lemonlabs"           %% "scala-uri"               % "3.0.0",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.1"
    )
  )
  .settings(
    libraryDependencies ++= flexmarkModules
  )
  .jvmPlatform(scalaVersions = AllScalaVersions)
  .settings(testSettings)
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings)

lazy val builders =
  projectMatrix
    .in(file("builders"))
    .dependsOn(core, searchIndex, searchFrontendPack, searchRetrieve)
    .settings(
      name := "subatomic-builders",
      libraryDependencies += "com.lihaoyi"                  %% "scalatags" % "0.9.1",
      libraryDependencies += "com.github.japgolly.scalacss" %% "core"      % "0.7.0",
      libraryDependencies += "com.monovore"                 %% "decline"   % "1.3.0"
    )
    .jvmPlatform(AllScalaVersions)
    .settings(testSettings)
    .enablePlugins(BuildInfoPlugin)
    .settings(buildInfoSettings)

lazy val searchFrontendPack = projectMatrix
  .in(file("search/pack"))
  .settings(name := "subatomic-search-frontend-pack")
  .jvmPlatform(AllScalaVersions)
  .settings(
    resourceGenerators in Compile +=
      Def.taskIf {
        if (sys.env.contains("CI")) {
          val out = (Compile / resourceManaged).value / "search.js"

          // doesn't matter which Scala version we use, it's compiled to JS anyways
          val fullOpt = (searchFrontend.js(Scala_213) / Compile / fullOptJS).value.data

          IO.copyFile(fullOpt, out)

          List(out)
        } else {
          val out = (Compile / resourceManaged).value / "search.js"

          // doesn't matter which Scala version we use, it's compiled to JS anyways
          val fullOpt = (searchFrontend.js(Scala_213) / Compile / fastOptJS).value.data

          IO.copyFile(fullOpt, out)

          List(out)

        }
      }.taskValue
  )

lazy val searchFrontend =
  projectMatrix
    .in(file("search/frontend"))
    .dependsOn(searchRetrieve, searchIndex)
    .settings(name := "subatomic-search-frontend")
    .settings(
      libraryDependencies += "com.raquo" %%% "laminar" % "0.11.0",
      scalaJSUseMainModuleInitializer := true
    )
    .jsPlatform(AllScalaVersions)
    .settings(testSettings)
    .settings(buildInfoSettings)
    .settings(batchModeOnCI)

lazy val searchCli =
  projectMatrix
    .in(file("search/cli"))
    .dependsOn(searchIndex, searchRetrieve)
    .settings(
      name := "subatomic-search-cli",
      libraryDependencies += "com.lihaoyi" %%% "os-lib" % "0.7.2",
      scalacOptions += "-Wconf:cat=unused-imports:wv",
      scalacOptions += "-Wconf:cat=unused-imports&site=subatomic.search.cli.SearchCLI:s,any:wv",
      libraryDependencies += "org.scala-lang.modules" %%% "scala-collection-compat" % "2.4.1"
    )
    .enablePlugins(JavaAppPackaging)
    .jvmPlatform(AllScalaVersions)
    .nativePlatform(AllScalaVersions)
    .settings(testSettings)
    .settings(buildInfoSettings)

lazy val searchIndex =
  projectMatrix
    .in(file("search/indexer"))
    .dependsOn(searchShared)
    .settings(name := "subatomic-search-indexer")
    .jvmPlatform(AllScalaVersions)
    .jsPlatform(AllScalaVersions, batchModeOnCI)
    .nativePlatform(AllScalaVersions)
    .settings(munitTestSettings)
    .settings(buildInfoSettings)

lazy val searchRetrieve =
  projectMatrix
    .in(file("search/retrieve"))
    .dependsOn(searchIndex)
    .settings(
      name := "subatomic-search-retrieve"
    )
    .jvmPlatform(AllScalaVersions)
    .jsPlatform(AllScalaVersions, batchModeOnCI)
    .nativePlatform(AllScalaVersions)
    .settings(munitTestSettings)
    .settings(buildInfoSettings)

lazy val searchShared =
  projectMatrix
    .in(file("search/shared"))
    .settings(
      name := "subatomic-search-shared",
      libraryDependencies += "com.lihaoyi" %%% "upickle" % "1.2.3"
    )
    .jvmPlatform(AllScalaVersions)
    .jsPlatform(AllScalaVersions, batchModeOnCI)
    .nativePlatform(AllScalaVersions)
    .settings(munitTestSettings)
    .settings(buildInfoSettings)
    .enablePlugins(BuildInfoPlugin)
    .settings(
      excludeFilter.in(headerSources) := HiddenFileFilter || "*Stemmer.scala"
    )

lazy val docs = project
  .in(file("docs"))
  .dependsOn(builders.jvm(Scala_212), plugin.jvm(Scala_212), searchIndex.jvm(Scala_212))
  .enablePlugins(SubatomicPlugin)
  .settings(
    scalaVersion := Scala_212,
    skip in publish := true,
    // To react to asset changes
    watchSources += WatchSource(
      (baseDirectory in ThisBuild).value / "docs" / "assets"
    ),
    watchSources += WatchSource(
      (baseDirectory in ThisBuild).value / "docs" / "pages"
    ),
    // To pick up Main.scala in docs/ (without the src/main/scala/ stuff)
    unmanagedSourceDirectories in Compile +=
      (baseDirectory in ThisBuild).value / "docs",
    libraryDependencies += "com.lihaoyi" %% "fansi" % "0.2.7",
    subatomicBuildersDependency := false,
    subatomicCoreDependency := false,
    subatomicInheritClasspath := true,
  )
/* .settings(buildInfoSettings) */

lazy val plugin = projectMatrix
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
        publishLocal in core.jvm(Scala_212),
        publishLocal in builders.jvm(Scala_212),
        publishLocal in searchIndex.jvm(Scala_212),
        publishLocal in searchFrontendPack.jvm(Scala_212),
        publishLocal in searchShared.jvm(Scala_212),
        publishLocal in searchRetrieve.jvm(Scala_212)
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

def ifNot[A](cond: Boolean, s: Seq[A]) = if (cond) Seq.empty else s

lazy val testSettings =
  Seq(
    libraryDependencies ++= ifNot(
      virtualAxes.value.contains(VirtualAxis.native),
      Seq(
        "com.disneystreaming" %%% "weaver-cats"       % "0.6.0-M6" % Test,
        "com.disneystreaming" %%% "weaver-scalacheck" % "0.6.0-M6" % Test
      )
    ),
    testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
    scalacOptions.in(Test) ~= filterConsoleScalacOptions
  )

lazy val munitTestSettings = Seq(
  libraryDependencies += "org.scalameta" %%% "munit"            % "0.7.21" % Test,
  libraryDependencies += "org.scalameta" %%% "munit-scalacheck" % "0.7.21" % Test,
  testFrameworks += new TestFramework("munit.Framework"),
  Test / scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
)

lazy val skipPublish = Seq(
  skip in publish := true
)

val batchModeOnCI =
  if (sys.env.contains("CI")) Seq(scalaJSLinkerConfig ~= {
    _.withBatchMode(true)
  })
  else Seq.empty

val platform = settingKey[String]("")

lazy val buildInfoSettings = {
  Seq(
    platform.withRank(KeyRanks.Invisible) := {
      val axes = virtualAxes.value

      if (axes.contains(VirtualAxis.jvm)) "jvm"
      else if (axes.contains(VirtualAxis.js)) "js"
      else "native"
    },
    buildInfoPackage.withRank(KeyRanks.Invisible) := "subatomic.internal",
    buildInfoKeys.withRank(KeyRanks.Invisible) := Seq[BuildInfoKey](
      version,
      scalaVersion,
      scalaBinaryVersion,
      platform
    )
  )
}

inThisBuild(
  List(
    scalaVersion := Scala_213,
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
    ),
    version := sys.env
      .getOrElse("VERSION_OVERRIDE", version.value)
  )
)

val scalafixRules = Seq(
  "OrganizeImports",
  "DisableSyntax",
  "LeakingImplicitClassVal",
  "ProcedureSyntax",
  "NoValInForComprehension"
).mkString(" ")

ThisBuild / commands += Command.command("preCI") { st =>
  s"scalafix --rules $scalafixRules" ::
    "test:scalafmtAll" ::
    "compile:scalafmtAll" ::
    "scalafmtSbt" ::
    "headerCreate" :: st
}

ThisBuild / commands += Command.command("ci") { st =>
  "clean" ::
    "compile" ::
    "test:compile" ::
    "fastLinkJS" ::
    "test:fastLinkJS" ::
    "test" ::
    "scripted" ::
    "scalafmtCheckAll" ::
    s"scalafix --check $scalafixRules" ::
    "headerCheck" :: st
}

addCommandAlias("buildSite", "docs/run build")

ThisBuild / concurrentRestrictions ++= {
  if (sys.env.contains("CI")) Seq(Tags.limitAll(4)) else Seq.empty
}
