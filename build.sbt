import java.io.FileReader
val Ver = new {
  val flexmark              = "0.62.2"
  val coursier              = "2.1.5"
  val osLib                 = "0.9.1"
  val scalaUri              = "4.0.2"
  val scalaCollectionCompat = "2.10.0"
  val scalatags             = "0.12.0"
  val decline               = "2.3.1"
  val laminar               = "16.0.0"
  val upickle               = "3.1.0"
  val fansi                 = "0.4.0"
  val weaver                = "0.6.15"
  val verify                = "1.0.0"
  val geny                  = "1.0.0"
  val scalaXml              = "2.1.0"
  val detective             = "0.0.2"
  val yank                  = "0.0.1"

  val Scala = new {
    val `2_12` = "2.12.15"
    val `2_13` = "2.13.10"
    val `3`    = "3.2.2"

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
  .settings(noCache)

import commandmatrix._

val flexmarkModules = Seq(
  "",
  "-ext-yaml-front-matter",
  "-ext-anchorlink",
  "-ext-autolink"
).map(mName => "com.vladsch.flexmark" % s"flexmark$mName" % Ver.flexmark)
import commandmatrix.extra._

lazy val disableScalafixForScala3 =
  MatrixAction.ForScala(_.isScala3).Configure(_.disablePlugins(ScalafixPlugin))

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
    libraryDependencies ++= {
      if (scalaVersion.value.startsWith("3."))
        Seq(
          ("io.get-coursier" %% "coursier" % Ver.coursier cross CrossVersion.for3Use2_13)
            .exclude("org.scala-lang.modules", "scala-collection-compat_2.13")
        )
      else
        Seq(
          "io.get-coursier" %% "coursier" % Ver.coursier
        )

    },
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "os-lib" % Ver.osLib,
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
  )(disableScalafixForScala3)
  .settings(testSettings)
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings)
  .settings(cacheSettings)

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
      libraryDependencies += "com.lihaoyi"       %% "requests" % "0.8.0"
    )
    .someVariations(
      Ver.Scala.all.toList,
      List(VirtualAxis.jvm)
    )(disableScalafixForScala3)
    .settings(testSettings)
    .enablePlugins(BuildInfoPlugin)
    .settings(buildInfoSettings)
    .settings(cacheSettings)

lazy val searchFrontendPack = projectMatrix
  .in(file("modules/search/pack"))
  .settings(name := "subatomic-search-frontend-pack")
  .someVariations(
    Ver.Scala.all.toList,
    List(VirtualAxis.jvm)
  )(disableScalafixForScala3)
  .settings(
    Compile / resourceGenerators +=
      Def.taskIf {
        if (sys.env.contains("CI")) {
          val out = (Compile / resourceManaged).value / "search.js"

          // doesn't matter which Scala version we use, it's compiled to JS anyways
          val fullOpt = (searchFrontend.js(
            Ver.Scala.`2_13`
          ) / Compile / fullOptJS).value.data

          IO.copyFile(fullOpt, out)

          List(out)
        } else {
          val out = (Compile / resourceManaged).value / "search.js"

          // doesn't matter which Scala version we use, it's compiled to JS anyways
          val fullOpt = (searchFrontend.js(
            Ver.Scala.`2_13`
          ) / Compile / fastOptJS).value.data

          IO.copyFile(fullOpt, out)

          List(out)

        }
      }.taskValue
  )
  .settings(cacheSettings)

lazy val searchFrontend =
  projectMatrix
    .in(file("modules/search/frontend"))
    .dependsOn(searchRetrieve, searchIndex)
    .settings(name := "subatomic-search-frontend")
    .settings(
      libraryDependencies += "com.raquo" %%% "laminar" % Ver.laminar,
      scalaJSUseMainModuleInitializer     := true
    )
    .someVariations(
      Ver.Scala.all.toList,
      List(VirtualAxis.js)
    )(disableScalafixForScala3, scalajsOverrides)
    .settings(testSettings)
    .settings(buildInfoSettings)
    .settings(cacheSettings)

lazy val searchCli =
  projectMatrix
    .in(file("modules/search/cli"))
    .dependsOn(searchIndex, searchRetrieve)
    .settings(
      name                                  := "subatomic-search-cli",
      libraryDependencies += "com.lihaoyi" %%% "os-lib" % Ver.osLib,
      libraryDependencies += "org.scala-lang.modules" %%% "scala-collection-compat" % Ver.scalaCollectionCompat
    )
    .enablePlugins(JavaAppPackaging)
    .someVariations(
      Ver.Scala.all.toList,
      List(VirtualAxis.jvm, VirtualAxis.native)
    )(
      disableScalafixForScala3,
      scalajsOverrides,
      MatrixAction((scalaV, axes) =>
        scalaV.isScala3 && axes.contains(VirtualAxis.native)
      ).Skip
    )
    .settings(testSettings)
    .settings(buildInfoSettings)
    .settings(cacheSettings)

lazy val searchIndex =
  projectMatrix
    .in(file("modules/search/indexer"))
    .dependsOn(searchShared)
    .settings(name := "subatomic-search-indexer")
    .someVariations(
      Ver.Scala.all.toList,
      List(VirtualAxis.jvm, VirtualAxis.js, VirtualAxis.native)
    )(
      disableScalafixForScala3,
      scalajsOverrides
    )
    .settings(munitTestSettings)
    .settings(buildInfoSettings)
    .settings(cacheSettings)

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
    )(
      disableScalafixForScala3,
      scalajsOverrides
    )
    .settings(munitTestSettings)
    .settings(buildInfoSettings)
    .settings(cacheSettings)

lazy val searchShared =
  projectMatrix
    .in(file("modules/search/shared"))
    .settings(
      name                                  := "subatomic-search-shared",
      libraryDependencies += "com.lihaoyi" %%% "upickle" % Ver.upickle,
      resolvers += Resolver.sonatypeRepo("snapshots")
    )
    .someVariations(
      Ver.Scala.all.toList,
      List(VirtualAxis.jvm, VirtualAxis.js, VirtualAxis.native)
    )(
      disableScalafixForScala3,
      scalajsOverrides
    )
    .settings(munitTestSettings)
    .settings(buildInfoSettings)
    .enablePlugins(BuildInfoPlugin)
    .settings(
      headerSources / excludeFilter := HiddenFileFilter || "*Stemmer.scala"
    )
    .settings(cacheSettings)

lazy val docs = projectMatrix
  .in(file("docs"))
  .dependsOn(builders)
  .someVariations(Ver.Scala.all.toList, List(VirtualAxis.jvm))(
    disableScalafixForScala3
  )
  .settings(
    publish / skip := true,
    // To react to asset changes
    watchSources += WatchSource(
      (ThisBuild / baseDirectory).value / "docs" / "assets"
    ),
    watchSources += WatchSource(
      (ThisBuild / baseDirectory).value / "docs" / "pages"
    ),
    watchSources += WatchSource(
      (ThisBuild / baseDirectory).value / "docs" / "blog"
    ),
    // To pick up Main.scala in docs/ (without the src/main/scala/ stuff)
    Compile / unmanagedSourceDirectories +=
      (ThisBuild / baseDirectory).value / "docs",
    libraryDependencies += "com.lihaoyi" %% "fansi" % Ver.fansi
  )
  .settings(Compile / resourceGenerators += Def.task {
    val properties = new java.util.Properties()
    val out =
      (Compile / unmanagedResourceDirectories).value.head / "subatomic.properties"

    val classpath =
      (Compile / classDirectory).value :: (Compile / dependencyClasspath).value.iterator
        .map(file => file.data)
        .toList

    properties.setProperty("variable.VERSION", version.value)

    properties.setProperty(
      "classpath.default",
      classpath.mkString(java.io.File.pathSeparator)
    )

    lazy val propsAreTheSame = {
      val p = (new java.util.Properties())
      p.load(new FileReader(out))
      streams.value.log
        .debug(
          s"Loaded properties are: $p, current properties are: $properties --- (${p.equals(properties)})"
        )
      properties.equals(p)
    }

    if (!out.exists() || !propsAreTheSame) {
      IO.write(properties, "props", out)

      Seq(out)
    } else Seq.empty
  })
  .settings(noCache)

lazy val plugin = projectMatrix
  .in(file("modules/sbt-plugin"))
  .withId("plugin")
  .settings(
    sbtPlugin                     := true,
    pluginCrossBuild / sbtVersion := "1.4.4"
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
  .settings(cacheSettings)

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
  libraryDependencies += "com.eed3si9n.verify" %%% "verify" % Ver.verify % Test,
  resolvers +=
    "Sonatype S01 OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots",
  libraryDependencies += "org.scalacheck" %%% "scalacheck" % "1.17.0" % Test,
  testFrameworks += new TestFramework("verify.runner.Framework")
)

lazy val skipPublish = Seq(
  publish / skip := true
)

lazy val noCache = Seq(
  pullRemoteCache := {},
  pushRemoteCache := {}
)

Global / onChangedBuildSource := ReloadOnSourceChanges

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
    semanticdbEnabled          := true,
    semanticdbVersion          := scalafixSemanticdb.revision,
    scalafixScalaBinaryVersion := scalaBinaryVersion.value,
    organization               := "com.indoorvivants",
    organizationName           := "Anton Sviridov",
    homepage  := Some(url("https://github.com/indoorvivants/subatomic")),
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
    version := (if (!sys.env.contains("CI")) "dev" else version.value)
  ) ++ noCache
)

val scalafixRules = Seq(
  "OrganizeImports",
  "DisableSyntax",
  "LeakingImplicitClassVal",
  "ProcedureSyntax",
  "NoValInForComprehension"
).mkString(" ")

ThisBuild / commands += Command.command("preCI") { st =>
  "Test/scalafmtAll" ::
    "scalafmtAll" ::
    "scalafmtSbt" ::
    "headerCreate" ::
    s"scalafix --rules $scalafixRules" ::
    st
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

addCommandAlias("buildSite", "docs3/runMain subatomic.docs.Docs build")
addCommandAlias("buildBlog", "docs3/runMain subatomic.docs.DevBlog build")

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
          Dimension.scala("2.13", fullFor3 = false),
          Dimension.platform()
        ),
        stubMissing = true
      ),
    commands ++=
      CrossCommand.all(
        Seq("pushRemoteCache", "pullRemoteCache"),
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
          Dimension
            .scala("2.12", fullFor3 = false), // "2.12" is the default one
          Dimension.platform()
        ),
        filter = axes =>
          CrossCommand.filter.isScalaBinary(2, Some(12))(axes) &&
            CrossCommand.filter.onlyJvm(axes),
        stubMissing = true
      )
  )
)

val cacheSettings = {
  def artifactName(nm: String, axes: Seq[VirtualAxis]) = {
    nm + axes
      .sortBy[Int] {
        case _: VirtualAxis.ScalaVersionAxis => 0
        case _: VirtualAxis.PlatformAxis     => 1
        case _: VirtualAxis.StrongAxis       => 2
        case _: VirtualAxis.WeakAxis         => 3
      }
      .map(_.idSuffix)
      .mkString("-", "-", "")
  }

  Seq(
    semanticdbIncludeInJar := true,
    Compile / packageCache / moduleName := artifactName(
      moduleName.value,
      virtualAxes.value
    ),
    Test / packageCache / moduleName := artifactName(
      moduleName.value,
      virtualAxes.value
    ),
    pushRemoteCacheTo := Some(
      MavenCache(
        "local-cache",
        (ThisBuild / baseDirectory).value / ".remote-cache"
      )
    )
  )
}

pushRemoteCache := ()
pullRemoteCache := ()
