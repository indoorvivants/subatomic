package com.indoorvivants.subatomic

import coursier._
import coursier.parse.DependencyParser

class MdocProcessor(
    scalaBinaryVersion: String = "2.13",
    mdocVersion: String = "2.2.9",
    extraCp: List[String] = Nil
) {

  def process(
      pwd: os.Path,
      file: os.Path,
      dependencies: List[String]
  ): os.Path = {
    val f = os.temp()

    val x = os
      .proc(
        "java",
        "-classpath",
        mainCp,
        "mdoc.Main",
        "--classpath",
        fetchCp(dependencies),
        "--in",
        file.toString(),
        "--out",
        f.toString()
      )
      .call(pwd, stderr = os.Inherit, stdout = os.Inherit)

    val _ = x.exitCode

    f
  }

  private val mdocDep = DependencyParser
    .dependency(s"org.scalameta::mdoc:$mdocVersion", scalaBinaryVersion)
    .getOrElse(throw new Exception("Unspeakable has happened"))

  private lazy val mainCp = {

    (Fetch()
      .addDependencies(mdocDep)
      .run()
      .map(_.getAbsolutePath()) ++ extraCp)
      .mkString(":")
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
}
