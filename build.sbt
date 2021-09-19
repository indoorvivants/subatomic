val Ver = new {
  val flexmark              = "0.62.2"
  val coursier              = "2.0.16"
  val osLib                 = "0.7.8"
  val scalaUri              = "3.2.0"
  val scalaCollectionCompat = "2.5.0"
  val scalatags             = "0.9.4"
  val scalacss              = "0.8.0-RC1"
  val scalacssFor2_12       = "0.7.0"
  val decline               = "2.1.0"
  val laminar               = "0.13.1"
  val upickle               = "1.4.0"
  val fansi                 = "0.2.14"
  val weaver                = "0.6.6"
  val munit                 = "0.7.29"

  val Scala = new {
    val `2_12` = "2.12.13"
    val `2_13` = "2.13.5"
    val `3`    = "3.0.2"

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

import commandmatrix._

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
      "io.get-coursier" %% "coursier" % Ver.coursier cross CrossVersion.for3Use2_13 exclude ("org.scala-lang.modules", "scala-collection-compat_2.13"),
      "com.lihaoyi"            %% "os-lib"                  % Ver.osLib,
      "org.scala-lang.modules" %% "scala-collection-compat" % Ver.scalaCollectionCompat
    )
    /* Test / fork := true */
  )
  .settings(
    libraryDependencies ++= flexmarkModules
  )
  .jvmPlatform(Ver.Scala.all)
  .settings(testSettings)
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings)
  .configure()

lazy val builders =
  projectMatrix
    .in(file("builders"))
    .dependsOn(core, searchIndex, searchFrontendPack, searchRetrieve)
    .settings(
      name := "subatomic-builders",
      libraryDependencies += {
        if (scalaVersion.value.startsWith("3"))
          ("com.lihaoyi" %% "scalatags" % Ver.scalatags)
            .exclude("com.lihaoyi", "geny_2.13") cross CrossVersion.for3Use2_13
        else
          ("com.lihaoyi" %% "scalatags" % Ver.scalatags)
      },
      libraryDependencies += {
        if (scalaBinaryVersion.value != "2.12")
          "com.github.japgolly.scalacss" %% "core" % Ver.scalacss
        else
          "com.github.japgolly.scalacss" %% "core" % Ver.scalacssFor2_12
      },
      libraryDependencies += "com.monovore" %% "decline" % Ver.decline
    )
    .jvmPlatform(Ver.Scala.all)
    .settings(testSettings)
    .enablePlugins(BuildInfoPlugin)
    .settings(buildInfoSettings)

lazy val searchFrontendPack = projectMatrix
  .in(file("search/pack"))
  .settings(name := "subatomic-search-frontend-pack")
  .jvmPlatform(Ver.Scala.all)
  .settings(
    Compile / resourceGenerators +=
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
      scalaJSUseMainModuleInitializer     := true
    )
    .jsPlatform(Ver.Scala.all)
    .settings(testSettings)
    .settings(buildInfoSettings)
    .settings(batchModeOnCI)

lazy val searchCli =
  projectMatrix
    .in(file("search/cli"))
    .dependsOn(searchIndex, searchRetrieve)
    .settings(
      name                                  := "subatomic-search-cli",
      libraryDependencies += "com.lihaoyi" %%% "os-lib" % Ver.osLib,
      scalacOptions += "-Wconf:cat=unused-imports:wv",
      scalacOptions += "-Wconf:cat=unused-imports&site=subatomic.search.cli.SearchCLI:s,any:wv",
      libraryDependencies += "org.scala-lang.modules" %%% "scala-collection-compat" % Ver.scalaCollectionCompat
    )
    .enablePlugins(JavaAppPackaging)
    .jvmPlatform(Ver.Scala.all)
    .nativePlatform(Ver.Scala.only_2)
    .settings(testSettings)
    .settings(buildInfoSettings)

lazy val searchIndex =
  projectMatrix
    .in(file("search/indexer"))
    .dependsOn(searchShared)
    .settings(name := "subatomic-search-indexer")
    .jvmPlatform(Ver.Scala.all)
    .jsPlatform(Ver.Scala.all, batchModeOnCI)
    .nativePlatform(Ver.Scala.only_2)
    .settings(munitTestSettings)
    .settings(buildInfoSettings)

lazy val searchRetrieve =
  projectMatrix
    .in(file("search/retrieve"))
    .dependsOn(searchIndex)
    .settings(
      name := "subatomic-search-retrieve"
    )
    .jvmPlatform(Ver.Scala.all)
    .jsPlatform(Ver.Scala.all, batchModeOnCI)
    .nativePlatform(Ver.Scala.only_2)
    .settings(munitTestSettings)
    .settings(buildInfoSettings)

lazy val searchShared =
  projectMatrix
    .in(file("search/shared"))
    .settings(
      name                                  := "subatomic-search-shared",
      libraryDependencies += "com.lihaoyi" %%% "upickle" % Ver.upickle
    )
    .jvmPlatform(Ver.Scala.all)
    .jsPlatform(Ver.Scala.all, batchModeOnCI)
    .nativePlatform(Ver.Scala.only_2)
    .settings(munitTestSettings)
    .settings(buildInfoSettings)
    .enablePlugins(BuildInfoPlugin)
    .settings(
      headerSources / excludeFilter := HiddenFileFilter || "*Stemmer.scala"
    )

lazy val docs = projectMatrix
  .in(file("docs"))
  .dependsOn(builders, plugin, searchIndex)
  .jvmPlatform(Seq(Ver.Scala.`2_12`))
  .enablePlugins(SubatomicPlugin)
  .settings(
    skip in publish := true,
    // To react to asset changes
    watchSources += WatchSource(
      (ThisBuild / baseDirectory).value / "docs" / "assets"
    ),
    watchSources += WatchSource(
      (ThisBuild / baseDirectory).value / "docs" / "pages"
    ),
    // To pick up Main.scala in docs/ (without the src/main/scala/ stuff)
    Compile / unmanagedSourceDirectories +=
      (ThisBuild / baseDirectory).value / "docs",
    libraryDependencies += "com.lihaoyi" %% "fansi" % Ver.fansi,
    subatomicBuildersDependency          := false,
    subatomicCoreDependency              := false,
    subatomicInheritClasspath            := true
  )

lazy val plugin = projectMatrix
  .in(file("sbt-plugin"))
  .withId("plugin")
  .settings(
    sbtPlugin                      := true,
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
        core.jvm(Ver.Scala.`2_12`) / publishLocal,
        builders.jvm(Ver.Scala.`2_12`) / publishLocal,
        searchIndex.jvm(Ver.Scala.`2_12`) / publishLocal,
        searchFrontendPack.jvm(Ver.Scala.`2_12`) / publishLocal,
        searchShared.jvm(Ver.Scala.`2_12`) / publishLocal,
        searchRetrieve.jvm(Ver.Scala.`2_12`) / publishLocal
      )
      .value
  )
  .settings(
    Compile / resourceGenerators += Def.task {
      val out =
        (Compile / managedResourceDirectories).value.head / "subatomic-plugin.properties"

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
        "com.disneystreaming" %%% "weaver-cats"       % Ver.weaver % Test,
        "com.disneystreaming" %%% "weaver-scalacheck" % Ver.weaver % Test
      )
    ),
    testFrameworks += new TestFramework("weaver.framework.CatsEffect")
  )

lazy val munitTestSettings = Seq(
  libraryDependencies += "org.scalameta" %%% "munit"            % Ver.munit % Test,
  libraryDependencies += "org.scalameta" %%% "munit-scalacheck" % Ver.munit % Test,
  testFrameworks += new TestFramework("munit.Framework"),
  Test / scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
)

lazy val skipPublish = Seq(
  publish / skip := true
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
    scalaVersion                                   := Ver.Scala.`2_13`,
    scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0",
    semanticdbEnabled                              := true,
    semanticdbVersion                              := scalafixSemanticdb.revision,
    scalafixScalaBinaryVersion                     := scalaBinaryVersion.value,
    organization                                   := "com.indoorvivants",
    organizationName                               := "Anton Sviridov",
    homepage                                       := Some(url("https://github.com/indoorvivants/subatomic")),
    startYear                                      := Some(2020),
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

addCommandAlias("buildSite", "docs2_12/run build")

import commandmatrix._

inThisBuild(
  Seq(
    commands ++=
      CrossCommand.all(
        Seq("compile", "test", "publishLocal"),
        matrices =
          Seq(core, searchShared, searchIndex, searchRetrieve, builders, searchCli, searchFrontend, searchFrontendPack),
        dimensions = Seq(
          Dimension.scala("2.13", fullFor3 = false),
          Dimension.platform()
        ),
        stubMissing = true
      ),
    commands ++=
      CrossCommand.composite(
        "codeQuality",
        Seq("scalafmtCheckAll", s"scalafix --check $scalafixRules", "headerCheck"),
        matrices =
          Seq(core, searchShared, searchIndex, searchRetrieve, builders, searchCli, searchFrontend, searchFrontendPack),
        dimensions = Seq(
          Dimension.scala("2.13", fullFor3 = false),
          Dimension.platform()
        ),
        filter = axes => CrossCommand.filter.notScala3(axes),
        stubMissing = true
      ),
    commands ++=
      CrossCommand.composite(
        "pluginTests",
        Seq("scripted"),
        matrices = Seq(plugin),
        dimensions = Seq(
          Dimension.scala("2.12", fullFor3 = false), // "2.12" is the default one
          Dimension.platform()
        ),
        filter = axes =>
          CrossCommand.filter.isScalaBinary(2, Some(12))(axes) &&
            CrossCommand.filter.onlyJvm(axes),
        stubMissing = true
      )
  )
)
