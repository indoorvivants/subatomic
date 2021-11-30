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

import subatomic.internal.BuildInfo

import coursier._
import coursier.parse.DependencyParser
import os.ProcessOutput

case class MdocFile(
    path: os.Path,
    config: MdocConfiguration
)

case class ScalaJSConfig(
    version: String,
    domVersion: String
)

object ScalaJSConfig {
  def default = ScalaJSConfig(version = "1.7.1", "1.0.0")

  def fromAttrs(attrs: Discover.YamlAttributes): Option[ScalaJSConfig] = {
    val enabled    = attrs.optionalOne("mdoc-js").map(_.toBoolean).getOrElse(false)
    val version    = attrs.optionalOne("mdoc-js-scalajs").map(_.trim).getOrElse(default.version)
    val domVersion = attrs.optionalOne("mdoc-js-dom-version").map(_.trim).getOrElse(default.domVersion)

    if (enabled) Some(ScalaJSConfig(version, domVersion)) else None
  }
}

case class MdocConfiguration(
    scalaBinaryVersion: String,
    scalaVersion: String,
    mdocVersion: String,
    inheritClasspath: Boolean,
    inheritVariables: Boolean,
    variables: Map[String, String],
    group: String,
    extraDependencies: Set[String],
    scalajsConfig: Option[ScalaJSConfig]
)

object MdocConfiguration {
  def default =
    MdocConfiguration(
      scalaBinaryVersion = BuildInfo.scalaBinaryVersion,
      scalaVersion = BuildInfo.scalaVersion,
      mdocVersion = "2.2.24",
      inheritClasspath = true,
      inheritVariables = true,
      variables = Map.empty,
      group = "default",
      extraDependencies = Set.empty,
      scalajsConfig = None
    )

  def fromAttrs(attrs: Discover.YamlAttributes): Option[MdocConfiguration] = {
    val defaultConfig = default
    val enabled       = attrs.optionalOne("mdoc").getOrElse("false").toBoolean
    val dependencies = attrs
      .optionalOne("mdoc-dependencies")
      .map(_.split(",").toList.map(_.trim).toSet)
      .getOrElse(default.extraDependencies)

    val scalaVersion = attrs.optionalOne("mdoc-scala").map(_.trim).getOrElse(defaultConfig.scalaBinaryVersion)
    val group        = attrs.optionalOne("mdoc-group").map(_.trim).getOrElse(defaultConfig.group)
    val version      = attrs.optionalOne("mdoc-version").map(_.trim).getOrElse(defaultConfig.mdocVersion)

    val scalajs = ScalaJSConfig.fromAttrs(attrs: Discover.YamlAttributes)

    val config = defaultConfig.copy(
      scalaBinaryVersion = scalaVersion,
      group = group,
      mdocVersion = version,
      extraDependencies = dependencies,
      scalajsConfig = scalajs
    )

    if (enabled) Some(config) else None
  }
}

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
      scala3CP + fetchCp(config.extraDependencies) + inheritedClasspath.map(":" + _).getOrElse("")

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
        val tmp = os.temp(deleteOnExit = false, contents = (base ++ args).mkString(" "))
        throw SubatomicError.mdocInvocationError(ex.toString(), files.map(_.toString()), tmp)
    }

    os.write.over(os.pwd / "mdoc-invocation.txt", (base ++ args).mkString(" "))

    filesWithTargets
  }
  def process(file: os.Path): os.Path = {
    processAll(Seq(file), None).head._2
  }

  private val mdocDep = {
    if (config.scalaBinaryVersion == "3")
      DependencyParser
        .dependency(s"org.scalameta:mdoc_3:${config.mdocVersion}", config.scalaBinaryVersion)
        .getOrElse(throw new Exception("oh noes"))
    else
      DependencyParser
        .dependency(s"org.scalameta::mdoc:${config.mdocVersion}", config.scalaBinaryVersion)
        .getOrElse(throw new Exception("Unspeakable has happened"))
  }

  private lazy val mainCp = {

    (Fetch()
      .addDependencies(mdocDep)
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
