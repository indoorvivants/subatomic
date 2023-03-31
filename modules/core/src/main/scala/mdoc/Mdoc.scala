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

import java.util.Properties

import coursier._
import coursier.core.MinimizedExclusions
import coursier.parse.DependencyParser
import os.ProcessOutput

case class MdocFile(
    path: os.Path,
    config: MdocConfiguration
)

class Mdoc(
    logger: Logger = Logger.default,
    config: MdocConfiguration = MdocConfiguration.default
) { self =>

  val logging = logger

  lazy val props = {
    val path        = "subatomic.properties"
    val classloader = this.getClass.getClassLoader
    val props       = new Properties()

    Option(classloader.getResourceAsStream(path)) match {
      case Some(stream) =>
        props.load(stream)
      case None =>
        logging.logLine(s"error: failed to load $path")
    }

    props
  }

  lazy val inheritedClasspath: Option[String] = if (config.inheritClasspath) {
    Option(props.getProperty(s"classpath.${config.group}"))
  } else None

  lazy val launcherClasspath: Option[String] =
    if (config.inheritClasspath)
      Option(props.getProperty(s"launcherClasspath.${config.group}"))
    else None

  lazy val inheritedVariables = if (config.inheritVariables) {
    import scala.jdk.CollectionConverters._

    props
      .stringPropertyNames()
      .asScala
      .filter(_.startsWith("variable."))
      .map { propName =>
        propName.drop("variable.".length()) -> props.getProperty(propName)
      }
      .toMap
  } else Map.empty[String, String]

  lazy val variablesStr: Seq[String] = {
    (inheritedVariables ++ config.variables).map { case (k, v) =>
      s"--site.$k=$v"
    }.toSeq
  }

  def processAll(
      files: Seq[os.Path],
      pwd: Option[os.Path]
  ): Seq[(os.Path, os.Path)] = {

    val logger      = logging.at("MDOC")
    val tmpLocation = os.temp.dir()

    val filesWithTargets = files.map { p =>
      p -> os.temp(dir = tmpLocation, suffix = ".md")
    }

    logger.logLine(
      files.mkString(", ")
    )

    if (config.extraDependencies.nonEmpty)
      logger.logLine(
        s"dependencies ${config.extraDependencies.mkString(", ")}"
      )

    if (inheritedClasspath.nonEmpty)
      logger.logLine(
        "inherited classpath from subatomic.properties resource"
      )

    if (inheritedVariables.nonEmpty)
      logger.logLine(s"inherited variables: $inheritedVariables")

    val args = filesWithTargets.flatMap { case (from, to) =>
      Seq("--in", from.toString, "--out", to.toString) ++ variablesStr
    }

    val launcherJVM = mainCp + launcherClasspath.map(":" + _).getOrElse("")

    val scala3CP = if (config.scalaBinaryVersion == "3") mainCp + ":" else ""

    val extraCP =
      scala3CP + fetchCp(config.extraDependencies) + inheritedClasspath
        .map(":" + _)
        .getOrElse("")

    val classpathArg =
      if (extraCP.trim != "")
        Seq("--classpath", extraCP)
      else
        Seq.empty

    val base = Seq(
      "java",
      "-cp",
      launcherJVM,
      "mdoc.Main"
    ) ++ classpathArg

    scala.util.Try(
      os
        .proc(base ++ args)
        .call(
          pwd.getOrElse(tmpLocation),
          stderr = ProcessOutput.Readlines(logger.at("ERR")._println),
          stdout = ProcessOutput.Readlines(logger.at("OUT")._println),
          propagateEnv = false
        )
    ) match {
      case scala.util.Success(_) =>
      case scala.util.Failure(ex) =>
        val tmp =
          os.temp(deleteOnExit = false, contents = (base ++ args).mkString(" "))
        throw SubatomicError.mdocInvocationError(
          ex.toString(),
          files.map(_.toString()),
          tmp
        )
    }

    os.write.over(os.pwd / "mdoc-invocation.txt", (base ++ args).mkString(" "))

    filesWithTargets
  }
  def process(file: os.Path): os.Path = {
    processAll(Seq(file), None).head._2
  }

  private val mdocDep = {
    if (config.scalaBinaryVersion == "3")
      simpleDep("org.scalameta", "mdoc_3", config.mdocVersion)
        .withMinimizedExclusions(
          MinimizedExclusions(
            Set(
              Organization("org.scala-lang") -> ModuleName("scala3-library_3"),
              Organization("org.scala-lang") -> ModuleName("scala3-compiler_3"),
              Organization("org.scala-lang") -> ModuleName("tasty-core_3")
            )
          )
        )
    else
      simpleDep(
        "org.scalameta",
        s"mdoc_${config.scalaBinaryVersion}",
        config.mdocVersion
      )
  }

  private val scala3Deps =
    if (config.scalaBinaryVersion == "3")
      Seq(
        simpleDep("org.scala-lang", "scala3-library_3", config.scalaVersion),
        simpleDep("org.scala-lang", "scala3-compiler_3", config.scalaVersion),
        simpleDep("org.scala-lang", "tasty-core_3", config.scalaVersion)
      )
    else Seq.empty

  private def simpleDep(org: String, artifact: String, version: String) =
    Dependency(
      Module(
        Organization(org),
        ModuleName(artifact)
      ),
      version
    )

  private lazy val mainCp = {

    (Fetch()
      .addDependencies(mdocDep)
      .addDependencies(scala3Deps: _*)
      .run()
      .map(_.getAbsolutePath()))
      .mkString(":")
  }

  private def fetchCp(deps: Iterable[String]) = {
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
}
