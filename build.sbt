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
    ).flatten *
  )
  .settings(skipPublish)

import commandmatrix._

val flexmarkModules = Seq(
  "",
  "-ext-yaml-front-matter",
  "-ext-anchorlink",
  "-ext-autolink"
).map(mName => "com.vladsch.flexmark" % s"flexmark$mName" % Ver.flexmark)
import commandmatrix.extra._

lazy val scalajsOverrides =
  MatrixAction.ForPlatforms(VirtualAxis.js).Settings {
    val base =
      if (sys.env.contains("CI"))
        Seq(
          scalaJSLinkerConfig ~= {
            _.withBatchMode(true)
          }
        )
      else Seq.empty
    base ++ Seq(Test / scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.CommonJSModule)
    })
  }

lazy val core = projectMatrix
  .in(file("modules/core"))
  .settings(
    name := "subatomic-core",
    // coursier is only published for Scala 2.13
    libraryDependencies +=
      ("io.get-coursier" %% "coursier" % Ver.coursier)
        .cross(CrossVersion.for3Use2_13)
        .excludeAll(
          ExclusionRule(
            "org.scala-lang.modules",
            "scala-collection-compat_2.13"
          )
        ),
    libraryDependencies ++= Seq(
      "org.scalameta" % "mdoc-interfaces" % Ver.mdoc,
      "com.lihaoyi"  %% "os-lib"          % Ver.osLib,
      // replaces the scala-collection-compat_2.13 excluded from coursier
      "org.scala-lang.modules" %% "scala-collection-compat" % Ver.scalaCollectionCompat,
      "io.lemonlabs" %% "scala-uri" % Ver.scalaUri
    )
  )
  .settings(
    libraryDependencies ++= flexmarkModules
  )
  .someVariations(
    Ver.Scala.all.toList,
    List(VirtualAxis.jvm)
  )(MatrixAction.ForAll.Keep)
  .settings(testSettings)
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings)

lazy val builders =
  projectMatrix
    .in(file("modules/builders"))
    .dependsOn(core, searchIndex, searchFrontendPack, searchRetrieve)
    .settings(
      name                                  := "subatomic-builders",
      libraryDependencies += "com.monovore" %% "decline"   % Ver.decline,
      libraryDependencies += "com.lihaoyi"  %% "geny"      % Ver.geny,
      libraryDependencies += "com.lihaoyi"  %% "scalatags" % Ver.scalatags,
      libraryDependencies += "com.indoorvivants.detective" %% "platform" % Ver.detective,
      libraryDependencies += "com.indoorvivants" %% "yank"     % Ver.yank,
      libraryDependencies += "com.lihaoyi"       %% "requests" % "0.8.3",
      libraryDependencies += "tech.neander"      %% "cue4s"    % Ver.cue4s
    )
    .someVariations(
      Ver.Scala.all.toList,
      List(VirtualAxis.jvm)
    )(MatrixAction.ForAll.Keep)
    .settings(testSettings)
    .enablePlugins(BuildInfoPlugin)
    .settings(buildInfoSettings)

lazy val searchFrontendPack = projectMatrix
  .in(file("modules/search/pack"))
  .settings(name := "subatomic-search-frontend-pack")
  .someVariations(
    Ver.Scala.all.toList,
    List(VirtualAxis.jvm)
  )(MatrixAction.ForAll.Keep)
  .settings(
    Compile / resourceGenerators +=
      Def.taskIf {
        if (sys.env.contains("CI")) {
          val out = (Compile / resourceManaged).value / "search.js"

          val fullOpt = (searchFrontend.js(
            Ver.Scala.`3`
          ) / Compile / fullOptJS).value.data

          IO.copyFile(fullOpt, out)

          Seq(out)
        } else {
          val out = (Compile / resourceManaged).value / "search.js"

          val fullOpt = (searchFrontend.js(
            Ver.Scala.`3`
          ) / Compile / fastOptJS).value.data

          IO.copyFile(fullOpt, out)

          Seq(out)

        }
      }.taskValue
  )

lazy val searchFrontend =
  projectMatrix
    .in(file("modules/search/frontend"))
    .dependsOn(searchRetrieve, searchIndex)
    .settings(name := "subatomic-search-frontend")
    .settings(
      libraryDependencies += "com.raquo" %% "laminar" % Ver.laminar,
      scalaJSUseMainModuleInitializer    := true
    )
    .someVariations(
      Ver.Scala.all.toList,
      List(VirtualAxis.js)
    )(scalajsOverrides)
    .settings(testSettings)
    .settings(buildInfoSettings)

lazy val searchCli =
  projectMatrix
    .in(file("modules/search/cli"))
    .dependsOn(searchIndex, searchRetrieve)
    .settings(
      name                                 := "subatomic-search-cli",
      libraryDependencies += "com.lihaoyi" %% "os-lib" % Ver.osLib,
      libraryDependencies += "org.scala-lang.modules" %% "scala-collection-compat" % Ver.scalaCollectionCompat
    )
    .enablePlugins(JavaAppPackaging)
    .someVariations(
      Ver.Scala.all.toList,
      List(VirtualAxis.jvm, VirtualAxis.native)
    )(scalajsOverrides)
    .settings(testSettings)
    .settings(buildInfoSettings)

lazy val searchIndex =
  projectMatrix
    .in(file("modules/search/indexer"))
    .dependsOn(searchShared)
    .settings(name := "subatomic-search-indexer")
    .someVariations(
      Ver.Scala.all.toList,
      List(VirtualAxis.jvm, VirtualAxis.js, VirtualAxis.native)
    )(scalajsOverrides)
    .settings(testSettings)
    .settings(buildInfoSettings)

lazy val searchRetrieve =
  projectMatrix
    .in(file("modules/search/retrieve"))
    .dependsOn(searchIndex)
    .settings(
      name := "subatomic-search-retrieve"
    )
    .someVariations(
      Ver.Scala.all.toList,
      List(VirtualAxis.jvm, VirtualAxis.js, VirtualAxis.native)
    )(scalajsOverrides)
    .settings(testSettings)
    .settings(buildInfoSettings)

lazy val searchShared =
  projectMatrix
    .in(file("modules/search/shared"))
    .settings(
      name                                 := "subatomic-search-shared",
      libraryDependencies += "com.lihaoyi" %% "upickle" % Ver.upickle
    )
    .someVariations(
      Ver.Scala.all.toList,
      List(VirtualAxis.jvm, VirtualAxis.js, VirtualAxis.native)
    )(scalajsOverrides)
    .settings(testSettings)
    .settings(buildInfoSettings)
    .enablePlugins(BuildInfoPlugin)
    .settings(
      headerSources / excludeFilter := HiddenFileFilter || "*Stemmer.scala"
    )

lazy val docs = projectMatrix
  .in(file("docs"))
  .dependsOn(builders)
  .someVariations(Ver.Scala.all.toList, List(VirtualAxis.jvm))(
    MatrixAction.ForAll.Keep
  )
  .settings(
    publish / skip      := true,
    fork                := true,
    run / baseDirectory := (ThisBuild / baseDirectory).value,
    // To react to asset changes
    watchTriggers ++= Seq("assets", "pages", "blog")
      .map(dir => (ThisBuild / baseDirectory).value.toGlob / "docs" / dir / **),
    // To pick up Main.scala in docs/ (without the src/main/scala/ stuff)
    Compile / unmanagedSourceDirectories +=
      (ThisBuild / baseDirectory).value / "docs",
    libraryDependencies += "com.lihaoyi" %% "fansi" % Ver.fansi
  )
  .settings(Compile / resourceGenerators += Def.task {
    val properties = new java.util.Properties()
    val out        = (Compile / resourceManaged).value / "subatomic.properties"
    val converter  = fileConverter.value

    val classpath =
      (Compile / classDirectory).value :: (Compile / dependencyClasspath).value.iterator
        .map(file => converter.toPath(file.data).toFile)
        .toList

    properties.setProperty("variable.VERSION", version.value)

    properties.setProperty(
      "classpath.default",
      classpath.mkString(java.io.File.pathSeparator)
    )

    IO.write(properties, "props", out)

    Seq(out)
  })

lazy val plugin = projectMatrix
  .in(file("modules/sbt-plugin"))
  .withId("plugin")
  .settings(
    sbtPlugin                     := true,
    pluginCrossBuild / sbtVersion := "2.1.0"
  )
  .jvmPlatform(scalaVersions = Seq(Ver.Scala.`3`))
  .settings(
    moduleName         := "subatomic-plugin",
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  )
  .settings(
    publishLocal := publishLocal
      .dependsOn(
        core.jvm(Ver.Scala.`3`) / publishLocal,
        builders.jvm(Ver.Scala.`3`) / publishLocal,
        searchIndex.jvm(Ver.Scala.`3`) / publishLocal,
        searchFrontendPack.jvm(Ver.Scala.`3`) / publishLocal,
        searchShared.jvm(Ver.Scala.`3`) / publishLocal,
        searchRetrieve.jvm(Ver.Scala.`3`) / publishLocal
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

      Seq(out)
    }
  )
  .enablePlugins(ScriptedPlugin, SbtPlugin)

lazy val testSettings =
  Seq(
    libraryDependencies ++= Seq(
      "org.typelevel"  %% "weaver-cats"       % Ver.weaver     % Test,
      "org.typelevel"  %% "weaver-scalacheck" % Ver.weaver     % Test,
      "org.scalacheck" %% "scalacheck"        % Ver.scalacheck % Test
    ),
    testFrameworks += new TestFramework("weaver.framework.CatsEffect")
  )

lazy val skipPublish = Seq(
  publish / skip := true
)

Global / onChangedBuildSource := ReloadOnSourceChanges

val subatomicPlatform = settingKey[String]("")

lazy val buildInfoSettings = {
  Seq(
    subatomicPlatform.withRank(KeyRanks.Invisible) := {
      val axes = virtualAxes.value

      if (axes.contains(VirtualAxis.jvm)) "jvm"
      else if (axes.contains(VirtualAxis.js)) "js"
      else "native"
    },
    buildInfoPackage.withRank(KeyRanks.Invisible) := "subatomic.internal",
    buildInfoKeys.withRank(KeyRanks.Invisible)    := Seq[BuildInfoKey](
      version,
      scalaVersion,
      scalaBinaryVersion,
      BuildInfoKey.map(subatomicPlatform) { case (_, v) => "platform" -> v },
      BuildInfoKey("mdocVersion"    -> Ver.mdoc),
      BuildInfoKey("scalaJSVersion" -> scalaJSVersion)
    )
  )
}

inThisBuild(
  List(
    scalaVersion      := Ver.Scala.`3`,
    semanticdbEnabled := true,
    organization      := "com.indoorvivants",
    organizationName  := "Anton Sviridov",
    homepage  := Some(uri("https://github.com/indoorvivants/subatomic")),
    startYear := Some(2020),
    licenses  := List(
      "Apache-2.0" -> uri("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "keynmol",
        "Anton Sviridov",
        "keynmol@gmail.com",
        uri("https://blog.indoorvivants.com")
      )
    ),
    version := (if (!sys.env.contains("CI")) "dev" else version.value)
  )
)

val scalafixRules = Seq(
  "OrganizeImports",
  "DisableSyntax",
  "LeakingImplicitClassVal",
  "NoValInForComprehension"
).mkString(" ")

ThisBuild / commands += Command.command("preCI") { st =>
  "scalafmtAll" ::
    "scalafmtSbt" ::
    "headerCreate" ::
    s"scalafix" ::
    st
}

ThisBuild / commands += Command.command("ci") { st =>
  "clean" ::
    "compile" ::
    "Test/compile" ::
    "fastLinkJS" ::
    "Test/fastLinkJS" ::
    "test" ::
    "scripted" ::
    "scalafmtCheckAll" ::
    s"scalafix --check $scalafixRules" ::
    "headerCheck" :: st
}

addCommandAlias("buildSite", "docs/runMain subatomic.docs.Docs build")
addCommandAlias("buildBlog", "docs/runMain subatomic.docs.DevBlog build")

import commandmatrix._

inThisBuild(
  Seq(
    commands ++=
      CrossCommand.all(
        Seq("compile", "test", "publishLocal"),
        matrices = Seq(
          core,
          builders,
          searchCli,
          searchFrontend,
          searchFrontendPack,
          searchIndex,
          searchRetrieve,
          searchShared
        ),
        dimensions = Seq(
          Dimension.scala("3", fullFor3 = false),
          Dimension.platform()
        ),
        stubMissing = true
      ),
    commands ++=
      CrossCommand.composite(
        "codeQuality",
        Seq(
          "scalafmtCheckAll",
          s"scalafix --check $scalafixRules",
          "headerCheck"
        ),
        matrices = Seq(
          core,
          searchShared,
          searchIndex,
          searchRetrieve,
          builders,
          searchCli,
          searchFrontend,
          searchFrontendPack
        ),
        dimensions = Seq(
          Dimension.scala("3", fullFor3 = false),
          Dimension.platform()
        ),
        stubMissing = true
      ),
    commands ++=
      CrossCommand.composite(
        "pluginTests",
        Seq("scripted"),
        matrices = Seq(plugin),
        dimensions = Seq(
          Dimension.scala("3", fullFor3 = false),
          Dimension.platform()
        ),
        filter = axes => CrossCommand.filter.onlyJvm(axes),
        stubMissing = true
      )
  )
)
