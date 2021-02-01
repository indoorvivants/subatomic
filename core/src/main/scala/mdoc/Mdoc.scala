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
import java.util.concurrent.ConcurrentHashMap

import subatomic.internal.BuildInfo

import coursier._
import coursier.parse.DependencyParser
import os.ProcessOutput

case class MdocFile(
    path: os.Path,
    dependencies: Set[String] = Set.empty,
    inheritedClasspath: Boolean = true
)

class Mdoc(
    scalaBinaryVersion: String = BuildInfo.scalaBinaryVersion,
    mdocVersion: String = "2.2.9",
    extraCp: List[String] = Nil,
    logger: Logger = Logger.default,
    inheritClasspath: Boolean = true,
    inheritVariables: Boolean = true,
    variables: Map[String, String] = Map.empty
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

  lazy val inheritedClasspath: Option[String] = if (inheritClasspath) {
    Option(props.getProperty("classpath"))
  } else None

  lazy val inheritedVariables = if (inheritVariables) {
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
    (inheritedVariables ++ variables).map {
      case (k, v) => s"--site.$k=$v"
    }.toSeq
  }

  case class MdocSettings(
      dependencies: Set[String]
  )

  class PreparedMdoc[T](
      processor: Mdoc,
      mapping: Map[MdocSettings, Vector[(T, MdocFile)]],
      pwd: Option[os.Path] = None
  ) {
    val processed = new ConcurrentHashMap[MdocSettings, Map[T, os.Path]]

    def get(content: T): os.Path = {
      val similarOnes = mapping.filter(_._2.map(_._1).contains(content))

      similarOnes.foreach {
        case (settings, pieces) =>
          processed.computeIfAbsent(
            settings,
            _ => {
              val paths = pieces.map(_._2.path)

              val result =
                processor.processAll(paths, settings.dependencies, pwd)

              result.map(_._2).zip(pieces.map(_._1)).map(_.swap).toMap
            }
          )
      }

      processed.get(similarOnes.head._1).apply(content)
    }
  }

  def processAll(
      files: Seq[os.Path],
      dependencies: Set[String],
      pwd: Option[os.Path]
  ) = {

    val logger      = logging.at("MDOC(batch)")
    val tmpLocation = os.temp.dir()

    val filesWithTargets = files.map { p =>
      p -> os.temp(dir = tmpLocation, suffix = ".md")
    }

    logger.logLine(
      files.mkString(", ")
    )

    if (dependencies.nonEmpty)
      logger.logLine(
        s"dependencies ${dependencies.mkString(", ")}"
      )

    if (inheritedClasspath.nonEmpty)
      logger.logLine(
        "inherited classpath from subatomic.properties resource"
      )

    if (inheritedVariables.nonEmpty)
      logger.logLine(s"inherited variables: $inheritedVariables")

    val args = filesWithTargets.flatMap {
      case (from, to) =>
        Seq("--in", from.toString, "--out", to.toString) ++ variablesStr
    }

    val base = Seq(
      "java",
      "-classpath",
      mainCp,
      "mdoc.Main",
      "--classpath",
      fetchCp(dependencies) + inheritedClasspath.map(":" + _).getOrElse("")
    )

    scala.util.Try(
      os
        .proc(base ++ args)
        .call(
          pwd.getOrElse(tmpLocation),
          stderr = ProcessOutput.Readlines(logger.at("ERR")._println),
          stdout = ProcessOutput.Readlines(logger.at("OUT")._println)
        )
    ) match {
      case scala.util.Success(_) =>
      case scala.util.Failure(ex) =>
        throw SubatomicError.mdocInvocationError(ex.toString(), files.map(_.toString()))
    }

    os.write.over(os.pwd / "mdoc-invocation.txt", (base ++ args).mkString(" "))

    filesWithTargets
  }

  def prepare[T](
      files: Iterable[(T, MdocFile)],
      pwd: Option[os.Path] = None
  ) = {
    val groupedByDependencies = files
      .groupBy { mf =>
        mf._2.dependencies
      }
      .map { case (deps, values) => MdocSettings(deps) -> values.toVector }

    new PreparedMdoc[T](self, groupedByDependencies.toMap, pwd)
  }

  def process(
      file: os.Path,
      dependencies: Set[String]
  ): os.Path = {
    val f = os.temp()

    val pwd = f / os.up

    val logger = logging.at("MDOC")

    logger.logLine(file.toString())

    if (dependencies.nonEmpty)
      logger.logLine(s"dependencies ${dependencies.mkString(", ")}")

    if (inheritedClasspath.nonEmpty)
      logger.logLine(
        "inherited classpath from subatomic.properties resource"
      )

    val args = Seq(
      "java",
      "-classpath",
      mainCp,
      "mdoc.Main",
      "--classpath",
      fetchCp(dependencies) + inheritedClasspath.map(":" + _).getOrElse(""),
      "--in",
      file.toString(),
      "--out",
      f.toString()
    ) ++ variablesStr

    os
      .proc(args)
      .call(
        pwd,
        stderr = ProcessOutput.Readlines(logger.at("ERR")._println),
        stdout = ProcessOutput.Readlines(logger.at("OUT")._println)
      )

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

  private def fetchCp(deps: Iterable[String]) = {
    Fetch()
      .addDependencies(
        deps.toSeq
          .map(DependencyParser.dependency(_, scalaBinaryVersion))
          .map(_.left.map(new RuntimeException(_)).toTry.get): _*
      )
      .run()
      .map(_.getAbsolutePath())
      .mkString(":")
  }
}
