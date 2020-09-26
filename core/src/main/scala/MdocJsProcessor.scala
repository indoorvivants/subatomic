package com.indoorvivants
package subatomic

import coursier.parse.DependencyParser
import coursier.core.Dependency
import coursier.Fetch

import ammonite.ops._

case class ScalaJsConfiguration(
    version: String = "1.1.1",
    domVersion: String = "1.0.0"
)

object ScalaJsConfiguration {
  val default = ScalaJsConfiguration()
}

class MdocJsProcessor(
    scalaBinaryVersion: String = "2.13",
    mdocVersion: String = "2.2.9",
    scalajsConfiguration: ScalaJsConfiguration = ScalaJsConfiguration.default
) {

  private lazy val runnerCp = cp(
    unsafeParse(s"org.scalameta::mdoc-js:$mdocVersion")
  ) + ":" + optsFolder.toString

  private lazy val optsFolder = {
    val jsClasspath = cp(
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

    val compilerPlug = cp(
      unsafeParse(
        s"org.scala-js:scalajs-compiler_$fullScala:${scalajsConfiguration.version}",
        transitive = false
      )
    )

    val tempDir = os.temp.dir()

    val fileContent =
      List(
        "js-classpath=" + jsClasspath,
        "js-scalac-options=-Xplugin:" + compilerPlug
      ).mkString("\n")

    os.write.over(tempDir / "mdoc.properties", fileContent)

    tempDir
  }

  private def fetchCp(deps: List[String]) = {
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
      pwd: os.Path,
      file: os.Path,
      dependencies: List[String]
  ): os.Path = {
    val tempDir = os.temp.dir()
    %%(
      "java",
      "-classpath",
      runnerCp,
      "mdoc.Main",
      "--classpath",
      fetchCp(dependencies),
      "--in",
      file.toString(),
      "--out",
      (tempDir / file.last).toString()
    )(pwd)

    tempDir / file.last
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

object MdocJsProcessor extends App {
  val mp = new MdocJsProcessor()

  println(mp.process(os.pwd, os.pwd / "test.md", Nil))
}
