import BuildSettings._

lazy val root = project
  .aggregate(
    Seq(
      docs.projectRefs,
      plugin.projectRefs,
      core.projectRefs,
      searchIndex.projectRefs,
      searchShared.projectRefs,
      searchFrontend.projectRefs,
      searchFrontendPack.projectRefs,
      searchRetrieve.projectRefs
    ).flatten: _*
  )
  .settings(skipPublish)

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
    )
  )
  .jvmPlatform(scalaVersions = AllScalaVersions)
  .settings(testSettings)
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings)

lazy val searchIndex =
  projectMatrix
    .in(file("search/indexer"))
    .dependsOn(searchShared)
    .settings(name := "subatomic-search-indexer")
    .jvmPlatform(AllScalaVersions)
    .jsPlatform(AllScalaVersions)
    .settings(testSettings)
    .settings(buildInfoSettings)

lazy val searchAll = project
  .aggregate(
    (searchIndex.projectRefs ++
      searchShared.projectRefs ++
      searchRetrieve.projectRefs ++
      searchFrontendPack.projectRefs): _*
  )
  .settings(
    skip in publish := true,
    skip in publishLocal := true
  )

lazy val searchFrontendPack = projectMatrix
  .in(file("search/pack"))
  .settings(name := "subatomic-search-frontend-pack")
  .jvmPlatform(AllScalaVersions)
  .settings(
    compile := {
      val _ = (searchFrontend.js(Scala_213) / Compile / fullOptJS).value

      (compile in Compile).value
    },
    resourceGenerators in Compile +=
      Def.task {
        val out = (Compile / resourceManaged).value / "search.js"

        // doesn't matter which Scala version we use, it's compiled to JS anyways
        val fullOpt =
          (searchFrontend.js(Scala_213) / Compile / fastOptJS).value.data

        IO.copyFile(fullOpt, out)

        List(out)
      }.taskValue
  )

// concurrentRestrictions in Global ++= {
//   if (sys.env.get("CI").nonEmpty) {
//     // all Scala.js projects ( get their IDs)
//     val jsProjects =
//       Seq(searchFrontend, searchIndex, searchRetrieve, searchShared)
//         .flatMap(_.filterProjects(Seq(VirtualAxis.js)).map(_.id))

//     val tags = for {
//       projectName <- jsProjects
//       stage       <- Seq(Compile.name, Test.name)
//       typ         <- Seq("fastopt", "fullopt")
//       name = s"uses-scalajs-linker-$projectName-$stage-$typ"
//     } yield Tags.Tag(name)

//     // only 2 concurrent linking processes can run
//     Seq(Tags.limitSum(1, tags: _*))
//   } else Seq.empty
// }

lazy val searchFrontend =
  projectMatrix
    .in(file("search/frontend"))
    .dependsOn(searchRetrieve, searchIndex % "compile->test")
    .settings(name := "subatomic-search-frontend")
    .settings(
      libraryDependencies += "com.raquo" %%% "laminar" % "0.11.0",
      scalaJSUseMainModuleInitializer := true
    )
    .jsPlatform(AllScalaVersions)
    .settings(testSettings)
    .settings(buildInfoSettings)

lazy val searchRetrieve =
  projectMatrix
    .in(file("search/retrieve"))
    .dependsOn(searchIndex % "compile->test")
    .settings(
      name := "subatomic-search-retrieve"
    )
    .jvmPlatform(AllScalaVersions)
    .jsPlatform(AllScalaVersions)
    .settings(testSettings)
    .settings(buildInfoSettings)

lazy val searchShared =
  projectMatrix
    .in(file("search/shared"))
    .settings(
      name := "subatomic-search-shared",
      libraryDependencies += "com.lihaoyi" %%% "upickle" % "1.2.2"
    )
    .jvmPlatform(AllScalaVersions)
    .jsPlatform(AllScalaVersions)
    .settings(testSettings)
    .settings(buildInfoSettings)
    .enablePlugins(BuildInfoPlugin)

lazy val docs = projectMatrix
  .in(file("docs"))
  .withId("docs")
  .dependsOn(core, plugin, searchIndex, searchFrontendPack)
  .jvmPlatform(scalaVersions = Seq(Scala_212))
  .enablePlugins(SubatomicPlugin)
  .settings(
    skip in publish := true,
    // To react to asset changes
    watchSources += WatchSource((baseDirectory in ThisBuild).value / "docs" / "assets"),
    watchSources += WatchSource((baseDirectory in ThisBuild).value / "docs" / "pages"),
    // To pick up Main.scala in docs/ (without the src/main/scala/ stuff)
    unmanagedSourceDirectories in Compile +=
      (baseDirectory in ThisBuild).value / "docs",
    libraryDependencies += "com.lihaoyi"  %% "scalatags" % "0.9.1",
    libraryDependencies += "com.monovore" %% "decline"   % "1.3.0",
    subatomicAddDependency := false,
    subatomicInheritClasspath := true
  )
  .settings(buildInfoSettings)

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

lazy val testSettings =
  Seq(
    libraryDependencies += "com.disneystreaming" %%% "weaver-framework"  % "0.5.0" % Test,
    libraryDependencies += "com.disneystreaming" %%% "weaver-scalacheck" % "0.5.0" % Test,
    testFrameworks += new TestFramework("weaver.framework.TestFramework"),
    scalacOptions.in(Test) ~= filterConsoleScalacOptions,
    fork in Test := false
  )

lazy val skipPublish = Seq(
  skip in publish := true
)

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
  s"scalafix --rules $scalafixRules",
  "test:scalafmtAll",
  "compile:scalafmtAll",
  "scalafmtSbt",
  "headerCreate"
).mkString(";")

addCommandAlias("ci", CICommands)

addCommandAlias("preCI", PrepareCICommands)

addCommandAlias("buildSite", "docs2_12/run")
