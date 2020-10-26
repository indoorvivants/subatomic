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

package com.indoorvivants.subatomic

import coursier.Fetch
import coursier.core.Dependency
import coursier.parse.DependencyParser

case class ScalaJsConfiguration(
    version: String = "1.1.1",
    domVersion: String = "1.0.0"
)

object ScalaJsConfiguration {
  val default = ScalaJsConfiguration()
}

case class ScalaJSResult(
    mdFile: os.Path,
    mdjsFile: os.Path,
    mdocFile: os.Path
)

class MdocJsProcessor(
    scalaBinaryVersion: String = "2.13",
    mdocVersion: String = "2.2.9",
    scalajsConfiguration: ScalaJsConfiguration = ScalaJsConfiguration.default
) {

  private lazy val runnerCp = cp(
    unsafeParse(s"org.scalameta::mdoc-js:$mdocVersion")
  )

  private lazy val jsClasspath = cp(
    unsafeParse(
      s"org.scala-js::scalajs-library:${scalajsConfiguration.version}"
    ),
    unsafeParse(
      s"org.scala-js::scalajs-dom_sjs1:${scalajsConfiguration.domVersion}"
    )
  )

  val fullScala = scalaBinaryVersion match {
    case "2.12" => "2.12.12"
    case "2.13" => "2.13.3"
  }

  private lazy val compilerPlug = cp(
    unsafeParse(
      s"org.scala-js:scalajs-compiler_$fullScala:${scalajsConfiguration.version}",
      transitive = false
    )
  )

  def optsFolder(deps: List[String]) = {
    val tempDir = os.temp.dir()

    val depsCp = if (deps.nonEmpty) ":" + fetchCp(deps) else ""

    val fileContent =
      List(
        "js-classpath=" + jsClasspath + depsCp,
        "js-scalac-options=-Xplugin:" + compilerPlug
      ).mkString("\n")

    os.write.over(tempDir / "mdoc.properties", fileContent)

    tempDir.toString
  }

  def fetchCp(deps: List[String]) = {
    Fetch()
      .addDependencies(
        deps
          .map(DependencyParser.dependency(_, scalaBinaryVersion))
          .map(_.left.map(new RuntimeException(_)).toTry.get): _*
      )
      .run()
      .map(_.getAbsolutePath())
      .mkString(":")
  }

  def process(
      _pwd: os.Path,
      file: os.Path,
      dependencies: List[String]
  ): ScalaJSResult = {
    val tempDir = os.temp.dir()
    val opts    = optsFolder(dependencies)

    os.proc(
      "java",
      "-classpath",
      runnerCp + ":" + opts,
      "mdoc.Main",
      "--classpath",
      fetchCp(dependencies),
      "--in",
      file.toString(),
      "--out",
      (tempDir / file.last).toString()
    ).call(_pwd, stderr = os.Inherit, stdout = os.Inherit)

    ScalaJSResult(
      tempDir / file.last,
      tempDir / (file.last + ".js"),
      tempDir / "mdoc.js"
    )
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
        scalaBinaryVersion
      )
      .getOrElse(throw new Exception("Unspeakable has happened"))
      .withTransitive(transitive)

  }

}
