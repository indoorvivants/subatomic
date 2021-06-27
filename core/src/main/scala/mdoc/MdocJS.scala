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

import coursier.Fetch
import coursier.core.Dependency
import coursier.parse.DependencyParser
import os.ProcessOutput

case class ScalaJSResult(
    mdFile: os.Path,
    mdjsFile: os.Path,
    mdocFile: os.Path
)

class MdocJS(
    config: MdocConfiguration,
    logger: Logger = Logger.default
) {

  val scalajsConfiguration = config.scalajsConfig.getOrElse(ScalaJSConfig.default)

  val logging = logger

  private lazy val runnerCp = cp(
    unsafeParse(s"org.scalameta::mdoc-js:${config.mdocVersion}")
  )

  val fullScala = config.scalaBinaryVersion match {
    case "2.12" => "2.12.12"
    case "2.13" => "2.13.3"
    case "3"    => "2.13.3"
  }

  def jsLibraryClasspath = {
    val scalaSuffix = config.scalaBinaryVersion match {
      case "2.13" => "2.13"
      case "2.12" => "2.12"
      case "3"    => "2.13"
    }

    cp(
      unsafeParse(
        s"org.scala-js:scalajs-library_$scalaSuffix:${scalajsConfiguration.version}"
      ),
      unsafeParse(
        s"org.scala-js:scalajs-dom_sjs1_$scalaSuffix:${scalajsConfiguration.domVersion}"
      )
    )
  }

  private lazy val compilerPlug =
    if (config.scalaBinaryVersion != "3")
      "-Xplugin:" + cp(
        unsafeParse(
          s"org.scala-js:scalajs-compiler_$fullScala:${scalajsConfiguration.version}",
          transitive = false
        )
      )
    else "-Xscalajs"

  def optsFolder(deps: Iterable[String]) = {
    val tempDir = os.temp.dir()

    val depsCp = if (deps.nonEmpty) ":" + fetchCp(deps) else ""

    val fileContent =
      List(
        "js-classpath=" + jsLibraryClasspath + depsCp,
        "js-scalac-options=" + compilerPlug
      ).mkString("\n")

    os.write.over(tempDir / "mdoc.properties", fileContent)

    tempDir.toString
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
    val tempDir      = os.temp.dir()
    val opts         = optsFolder(dependencies)

    val logger = logging.at("MDOC.JS")

    val deps =
      if (dependencies.nonEmpty) s" [${dependencies.mkString(", ")}]" else ""

    logger.logLine(s"$files, dependencies: $deps")

    val mapping = files.map { p =>
      val tmp = os.temp.dir(dir = tempDir)

      p -> tmp / p.last
    }.toMap

    val argmap = mapping.toSeq.flatMap {
      case (from, to) =>
        Seq("--in", from.toString, "--out", to.toString)
    }

    os.proc(
      "java",
      "-classpath",
      runnerCp + ":" + opts,
      "mdoc.Main",
      "--classpath",
      fetchCp(dependencies),
      argmap
    ).call(
      _pwd,
      stderr = ProcessOutput.Readlines(logger.at("ERR")._println),
      stdout = ProcessOutput.Readlines(logger.at("OUT")._println)
    )

    mapping.toSeq.map {
      case (source, target) =>
        val tgDir = target / os.up

        source -> ScalaJSResult(target, tgDir / (source.last + ".js"), tgDir / "mdoc.js")
    }

  }

  private def cp(dep: Dependency*) = {
    (Fetch()
      .addDependencies(dep: _*)
      .run()
      .map(_.getAbsolutePath()))
      .mkString(":")
  }

  private def unsafeParse(d: String, transitive: Boolean = true) = {

    DependencyParser
      .dependency(
        d,
        config.scalaBinaryVersion
      )
      .getOrElse(throw new Exception("Unspeakable has happened"))
      .withTransitive(transitive)

  }

}
