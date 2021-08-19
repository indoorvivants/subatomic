val Ver = new {
  val flexmark              = "0.62.2"
  val coursier              = "2.0.16"
  val osLib                 = "0.7.8"
  val scalaUri              = "3.2.0"
  val scalaCollectionCompat = "2.5.0"
  val scalatags             = "0.9.4"
  val scalacss              = "0.7.0"
  val decline               = "2.0.0"
  val laminar               = "0.13.0"
  val upickle               = "1.4.0"
  val fansi                 = "0.2.14"
  val weaver                = "0.6.4"
  val munit                 = "0.7.26"

  val Scala = new {
    val `2_12` = "2.12.13"
    val `2_13` = "2.13.5"
    val `3`    = "3.0.1"

    val only_2    = Seq(`2_12`, `2_13`)
    val only_2_13 = Seq(`2_13`)
    val all       = only_2 :+ `3`
  }
}

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
).map(mName => "com.vladsch.flexmark" % s"flexmark$mName" % Ver.flexmark)

lazy val core = projectMatrix
  .in(file("core"))
  .settings(
    name := "subatomic-core",
    libraryDependencies ++= Seq(
      "io.get-coursier"        %% "coursier"                % Ver.coursier,
      "com.lihaoyi"            %% "os-lib"                  % Ver.osLib,
      "io.lemonlabs"           %% "scala-uri"               % Ver.scalaUri,
      "org.scala-lang.modules" %% "scala-collection-compat" % Ver.scalaCollectionCompat
    )
  )
  .settings(
    libraryDependencies ++= flexmarkModules
  )
  .jvmPlatform(Ver.Scala.only_2)
  .settings(testSettings)
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings)

lazy val builders =
  projectMatrix
    .in(file("builders"))
    .dependsOn(core, searchIndex, searchFrontendPack, searchRetrieve)
    .settings(
      name := "subatomic-builders",
      libraryDependencies += "com.lihaoyi"                  %% "scalatags" % Ver.scalatags,
      libraryDependencies += "com.github.japgolly.scalacss" %% "core"      % Ver.scalacss,
      libraryDependencies += "com.monovore"                 %% "decline"   % Ver.decline
    )
    .jvmPlatform(Ver.Scala.only_2)
    .settings(testSettings)
    .enablePlugins(BuildInfoPlugin)
    .settings(buildInfoSettings)

lazy val searchFrontendPack = projectMatrix
  .in(file("search/pack"))
  .settings(name := "subatomic-search-frontend-pack")
  .jvmPlatform(Ver.Scala.only_2)
  .settings(
    resourceGenerators in Compile +=
      Def.taskIf {
        if (sys.env.contains("CI")) {
          val out = (Compile / resourceManaged).value / "search.js"

          // doesn't matter which Scala version we use, it's compiled to JS anyways
          val fullOpt = (searchFrontend.js(Ver.Scala.`2_13`) / Compile / fullOptJS).value.data

          IO.copyFile(fullOpt, out)

          List(out)
        } else {
          val out = (Compile / resourceManaged).value / "search.js"

          // doesn't matter which Scala version we use, it's compiled to JS anyways
          val fullOpt = (searchFrontend.js(Ver.Scala.`2_13`) / Compile / fastOptJS).value.data

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
      libraryDependencies += "com.raquo" %%% "laminar" % Ver.laminar,
      scalaJSUseMainModuleInitializer := true
    )
    .jsPlatform(Ver.Scala.only_2_13)
    .settings(testSettings)
    .settings(buildInfoSettings)
    .settings(batchModeOnCI)

lazy val searchCli =
  projectMatrix
    .in(file("search/cli"))
    .dependsOn(searchIndex, searchRetrieve)
    .settings(
      name := "subatomic-search-cli",
      libraryDependencies += "com.lihaoyi" %%% "os-lib" % Ver.osLib,
      scalacOptions += "-Wconf:cat=unused-imports:wv",
      scalacOptions += "-Wconf:cat=unused-imports&site=subatomic.search.cli.SearchCLI:s,any:wv",
      libraryDependencies += "org.scala-lang.modules" %%% "scala-collection-compat" % Ver.scalaCollectionCompat
    )
    .enablePlugins(JavaAppPackaging)
    .jvmPlatform(Ver.Scala.only_2)
    .nativePlatform(Ver.Scala.only_2_13)
    .settings(testSettings)
    .settings(buildInfoSettings)

lazy val searchIndex =
  projectMatrix
    .in(file("search/indexer"))
    .dependsOn(searchShared)
    .settings(name := "subatomic-search-indexer")
    .jvmPlatform(Ver.Scala.only_2)
    .jsPlatform(Ver.Scala.only_2_13, batchModeOnCI)
    .nativePlatform(Ver.Scala.only_2_13)
    .settings(munitTestSettings)
    .settings(buildInfoSettings)

lazy val searchRetrieve =
  projectMatrix
    .in(file("search/retrieve"))
    .dependsOn(searchIndex)
    .settings(
      name := "subatomic-search-retrieve"
    )
    .jvmPlatform(Ver.Scala.only_2)
    .jsPlatform(Ver.Scala.only_2_13, batchModeOnCI)
    .nativePlatform(Ver.Scala.only_2_13)
    .settings(munitTestSettings)
    .settings(buildInfoSettings)

lazy val searchShared =
  projectMatrix
    .in(file("search/shared"))
    .settings(
      name := "subatomic-search-shared",
      libraryDependencies += "com.lihaoyi" %%% "upickle" % Ver.upickle
    )
    .jvmPlatform(Ver.Scala.only_2)
    .jsPlatform(Ver.Scala.only_2_13, batchModeOnCI)
    .nativePlatform(Ver.Scala.only_2_13)
    .settings(munitTestSettings)
    .settings(buildInfoSettings)
    .enablePlugins(BuildInfoPlugin)
    .settings(
      excludeFilter.in(headerSources) := HiddenFileFilter || "*Stemmer.scala"
    )

lazy val docs = project
  .in(file("docs"))
  .dependsOn(builders.jvm(Ver.Scala.`2_12`), plugin.jvm(Ver.Scala.`2_12`), searchIndex.jvm(Ver.Scala.`2_12`))
  .enablePlugins(SubatomicPlugin)
  .settings(
    scalaVersion := Ver.Scala.`2_12`,
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
    libraryDependencies += "com.lihaoyi" %% "fansi" % Ver.fansi,
    subatomicBuildersDependency := false,
    subatomicCoreDependency := false,
    subatomicInheritClasspath := true
  )

lazy val plugin = projectMatrix
  .in(file("sbt-plugin"))
  .withId("plugin")
  .settings(
    sbtPlugin := true,
    sbtVersion in pluginCrossBuild := "1.4.4"
  )
  .jvmPlatform(scalaVersions = Seq(Ver.Scala.`2_12`))
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
        publishLocal in core.jvm(Ver.Scala.`2_12`),
        publishLocal in builders.jvm(Ver.Scala.`2_12`),
        publishLocal in searchIndex.jvm(Ver.Scala.`2_12`),
        publishLocal in searchFrontendPack.jvm(Ver.Scala.`2_12`),
        publishLocal in searchShared.jvm(Ver.Scala.`2_12`),
        publishLocal in searchRetrieve.jvm(Ver.Scala.`2_12`)
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
        "com.disneystreaming" %%% "weaver-cats"       % "0.6.4" % Test,
        "com.disneystreaming" %%% "weaver-scalacheck" % "0.6.4" % Test
      )
    ),
    testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
    scalacOptions.in(Test) ~= filterConsoleScalacOptions
  )

lazy val munitTestSettings = Seq(
  libraryDependencies += "org.scalameta" %%% "munit"            % "0.7.26" % Test,
  libraryDependencies += "org.scalameta" %%% "munit-scalacheck" % "0.7.26" % Test,
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
    scalaVersion := Ver.Scala.`2_13`,
    scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0",
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
