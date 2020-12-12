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

case class MdocFile(
    path: os.Path,
    dependencies: Set[String] = Set.empty,
    inheritedClasspath: Boolean = true
)

class Mdoc(
    scalaBinaryVersion: String = BuildInfo.scalaBinaryVersion,
    mdocVersion: String = "2.2.9",
    extraCp: List[String] = Nil
) { self =>

  lazy val inheritedClasspath = {
    val path        = "subatomic.properties"
    val classloader = this.getClass.getClassLoader
    val props       = new Properties()

    Option(classloader.getResourceAsStream(path)) match {
      case Some(stream) =>
        props.load(stream)
      case None =>
        println(s"error: failed to load $path")
    }

    Option(props.getProperty("classpath"))
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
              println(s"Computing for $settings (on ${Thread.currentThread.getName})")
              val paths = pieces.map(_._2.path)

              val result = processor.processAll(paths, settings.dependencies, pwd)

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
    val tmpLocation = os.temp.dir()

    val filesWithTargets = files.map { p =>
      p -> os.temp(dir = tmpLocation, suffix = ".md")
    }

    logger.logLine(
      "[MDOC batch]: " + files.mkString(", ")
    )

    if (dependencies.nonEmpty)
      logger.logLine(
        s"[MDOC batch]: dependencies ${dependencies.mkString(", ")}"
      )

    if (inheritedClasspath.nonEmpty)
      logger.logLine(
        "[MDOC batch]: inherited classpath from subatomic.properties resource"
      )

    val args = filesWithTargets.flatMap {
      case (from, to) =>
        Seq("--in", from.toString, "--out", to.toString)
    }

    val base = Seq(
      "java",
      "-classpath",
      mainCp,
      "mdoc.Main",
      "--classpath",
      fetchCp(dependencies) + inheritedClasspath.map(":" + _).getOrElse("")
    )

    os
      .proc(base ++ args)
      .call(
        pwd.getOrElse(tmpLocation),
        stderr = os.Inherit,
        stdout = os.Inherit
      )

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

    logger.logLine(
      "[MDOC]: " + file
    )

    if (dependencies.nonEmpty)
      logger.logLine(s"[MDOC]: dependencies ${dependencies.mkString(", ")}")

    if (inheritedClasspath.nonEmpty)
      logger.logLine(
        "[MDOC]: inherited classpath from subatomic.properties resource"
      )

    os
      .proc(
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
      )
      .call(pwd, stderr = os.Inherit, stdout = os.Inherit)

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
