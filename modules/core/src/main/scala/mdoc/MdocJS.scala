/*
 * Copyright 2020 Anton Sviridov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package subatomic

import scala.annotation.nowarn

import coursier.Fetch
import coursier.core.Dependency
import coursier.parse.DependencyParser
import os.ProcessOutput
import java.util.Properties
import java.io.FileWriter

case class ScalaJSResult(
    mdFile: os.Path,
    mdjsFile: os.Path,
    mdocFile: os.Path
)

class MdocJS(
    config: MdocConfiguration,
    logger: Logger = Logger.default
) {

  val scalajsConfiguration =
    config.scalajsConfig.getOrElse(ScalaJSConfig.default)

  val logging = logger

  private val scalajsLinkerClasspath =
    if (config.scalaBinaryVersion == "2.12")
      Classpath.dependencies(
        s"org.scala-js:scalajs-linker_2.12:${scalajsConfiguration.version}"
        // s"org.scala-js:scalajs-ir_2.12:${scalajsConfiguration.version}"
      )
    else
      Classpath.dependencies(
        s"org.scala-js:scalajs-linker_2.13:${scalajsConfiguration.version}"
        // s"org.scala-js:scalajs-ir_2.13:${scalajsConfiguration.version}"
      )

  val scala3Modules =
    Set(
      "scala3-compiler_3",
      "tasty-core_3",
      "scala3-library_3",
      "scala3-interfaces"
    )

  private val mdocJSClasspath: Classpath.Dep =
    if (config.scalaBinaryVersion == "3")
      Classpath.Dep(
        s"org.scalameta:mdoc-js_3:${config.mdocVersion}",
        exclusions = scala3Modules.map(
          "org.scala-lang" -> _
        )
      )
    else
      Classpath.Dep(
        s"org.scalameta:mdoc-js_${config.scalaBinaryVersion}:${config.mdocVersion}"
      )

  private val scala3CP =
    if (config.scalaBinaryVersion == "3")
      Classpath.dependencies(
        scala3Modules
          .map(m => s"org.scala-lang:$m:${config.scalaVersion}")
          .toSeq: _*
      )
    else Classpath.empty

  private val workerCP =
    if (config.scalaBinaryVersion == "3")
      Classpath.Dep(s"org.scalameta:mdoc-js-worker_3:${config.mdocVersion}").cp
    else
      Classpath
        .Dep(
          s"org.scalameta:mdoc-js-worker_${config.scalaBinaryVersion}:${config.mdocVersion}"
        )
        .cp

  val scalajsLibraryClasspath = {
    val scalaSuffix = config.scalaBinaryVersion match {
      case "2.13" | "3" => "2.13"
      case "2.12"       => "2.12"
    }

    val cp1 = Classpath
      .Dep(
        s"org.scala-js:scalajs-library_$scalaSuffix:${scalajsConfiguration.version}"
      )
      .cp

    val cp2 = Classpath
      .Dep(
        s"org.scala-js:scalajs-dom_sjs1_$scalaSuffix:${scalajsConfiguration.domVersion}"
      )
      .cp

    cp1 ++ cp2
  }

  val scala3JSLibraryClasspath =
    if (config.scalaBinaryVersion == "3") {
      Classpath.dependencies(
        s"org.scala-lang:scala3-library_sjs1_3:${config.scalaVersion}"
      )
    } else Classpath.empty

  private lazy val compilerPlug = {
    if (config.scalaBinaryVersion != "3")
      "-Xplugin:" + cp(
        unsafeParse(
          s"org.scala-js:scalajs-compiler_${config.scalaVersion}:${scalajsConfiguration.version}",
          transitive = false
        )
      )
    else "-scalajs"
  }

  def optsFolder(deps: Iterable[String]) = {
    val tempDir = os.temp.dir()

    val dependenciesClasspath =
      if (deps.nonEmpty) Classpath.dependencies(deps.toSeq: _*)
      else Classpath.empty

    val props = new Properties()

    val jsClasspath =
      (scalajsLibraryClasspath ++ dependenciesClasspath ++ scala3JSLibraryClasspath)
    println(jsClasspath.toStringPretty("js-classpath"))

    val jsLinkerClasspath = (scalajsLinkerClasspath ++ workerCP)
    println(jsLinkerClasspath.toStringPretty("js-linker-classpath"))

    props
      .put(
        "js-classpath",
        jsClasspath.render(config)
      )

    props.put(
      "js-linker-classpath",
      jsLinkerClasspath.render(config)
    )
    props.put("js-scalac-options", compilerPlug)

    // val fileContent =
    //   List(
    //     "js-classpath=" + (scalajsLibraryClasspath ++ dependenciesClasspath ++ scala3JSLibraryClasspath)
    //       .render(config),
    //     "js-linker-classpath=" + scalajsLinkerClasspath.render(config),
    //     "js-scalac-options=" + compilerPlug
    //   ).mkString("\n")

    // os.write.over(tempDir / "mdoc.properties", fileContent)

    val w = new FileWriter((tempDir / "mdoc.properties").toIO)

    props.store(w, "")

    // ((tempDir / "mdoc.properties").toIO.formatted)

    tempDir
  }

  def fetchCp(deps: Iterable[String]) = {
    Fetch()
      .addDependencies(
        deps.toSeq
          .map(DependencyParser.dependency(_, config.scalaBinaryVersion))
          .map(_.left.map(new RuntimeException(_)).toTry.get): _*
      )
      .run()
      .map(_.getAbsolutePath())
      .mkString(":")
  }

  def processAll(
      _pwd: os.Path,
      files: Seq[os.Path]
  ): Seq[(os.Path, ScalaJSResult)] = {
    val dependencies = config.extraDependencies
    val tempDir      = os.temp.dir(deleteOnExit = false)
    val opts         = Classpath.Path(optsFolder(dependencies))

    val logger = logging.at("MDOC.JS")

    val deps =
      if (dependencies.nonEmpty) s" [${dependencies.mkString(", ")}]" else ""

    logger.logLine(s"$files, dependencies: $deps")

    val mapping = files.map { p =>
      val tmp = os.temp.dir(dir = tempDir)

      p -> tmp / p.last
    }.toMap

    val argmap = mapping.toSeq.flatMap { case (from, to) =>
      Seq("--in", from.toString, "--out", to.toString)
    }

    val launchClasspath =
      (mdocJSClasspath.cp ++ scala3CP ++ opts.cp)

    println(launchClasspath.toStringPretty("mdoc java"))

    val command =
      Seq(
        "java",
        "-classpath",
        launchClasspath.render(config),
        "mdoc.Main",
        "--classpath",
        launchClasspath.render(config)
      ) ++ argmap

    os
      .proc(command)
      .call(
        _pwd,
        stderr = ProcessOutput.Readlines(logger.at("ERR")._println),
        stdout = ProcessOutput.Readlines(logger.at("OUT")._println)
      ): @nowarn

    mapping.toSeq.map { case (source, target) =>
      val tgDir = target / os.up

      source -> ScalaJSResult(
        target,
        tgDir / (source.last + ".js"),
        tgDir / "mdoc.js"
      )
    }

  }

  private def cp(dep: Dependency*) = {
    (Fetch()
      .addDependencies(dep: _*)
      .run()
      .map(_.getAbsolutePath()))
      .mkString(":")
  }

  private def unsafeParse(d: String, transitive: Boolean) = {

    DependencyParser
      .dependency(
        d,
        config.scalaBinaryVersion
      )
      .getOrElse(throw new Exception("Unspeakable has happened"))
      .withTransitive(transitive)

  }

}
