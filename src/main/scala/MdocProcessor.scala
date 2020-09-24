package com.indoorvivants.subatomic

import coursier._

import scala.collection.JavaConverters._

import ammonite.ops._

import coursier.parse.DependencyParser

class MdocProcessor(
    scalaBinaryVersion: String = "2.13",
    mdocVersion: String = "2.2.9",
    extraCp: List[String] = Nil
) {

  val mdocDep = DependencyParser
    .dependency(s"org.scalameta::mdoc:$mdocVersion", scalaBinaryVersion)
    .right
    .get

  lazy val mainCp = {

    (Fetch()
      .addDependencies(mdocDep)
      .run()
      .seq
      .map(_.getAbsolutePath()) ++ extraCp)
      .mkString(":")
  }

  private def fetchCp(deps: List[String])= {
    Fetch()
      .addDependencies(
        deps
          .map(DependencyParser.dependency(_, scalaBinaryVersion))
          .map(_.right.get): _*
      )
      .run()
      .map(_.getAbsolutePath())
      .mkString(":")
  }

  def process(pwd: os.Path, file: os.Path, dependencies: List[String]) = {
    val f = os.temp()
    %%(
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
    )(pwd)

    f
  }
}
