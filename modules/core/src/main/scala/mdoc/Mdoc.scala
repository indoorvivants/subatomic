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

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Properties
import java.util.ServiceLoader

import scala.jdk.CollectionConverters._

import coursier._
import coursier.core.MinimizedExclusions
import coursier.parse.DependencyParser
import _root_.mdoc.interfaces.DiagnosticSeverity
import _root_.mdoc.{interfaces => mdi}

case class MdocFile(
    path: os.Path,
    config: MdocConfiguration
)

/** Runs mdoc in-process, using the `mdoc-interfaces` API.
  *
  * mdoc (and the Scala version it's built for) is fetched with coursier and
  * loaded in an isolated classloader – see [[MdocClassLoader]].
  */
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
        try props.load(stream)
        finally stream.close()
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
    props
      .stringPropertyNames()
      .asScala
      .filter(_.startsWith("variable."))
      .map { propName =>
        propName.drop("variable.".length()) -> props.getProperty(propName)
      }
      .toMap
  } else Map.empty[String, String]

  lazy val variables: Map[String, String] =
    inheritedVariables ++ config.variables

  def processAll(
      files: Seq[os.Path],
      pwd: Option[os.Path]
  ): Seq[(os.Path, os.Path)] = {

    val logger    = logging.at("MDOC")
    val outputDir = os.temp.dir()

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

    val workingDir = pwd.getOrElse(outputDir)
    val jVariables = variables.asJava

    val reporter = new MdocReporterStream(logger.at("OUT"))

    withMdoc(workingDir, reporter) { mdoc =>
      files.map { file =>
        reporter.reset()

        val evaluated = mdoc.evaluateMarkdownDocument(
          relativeFilename(file, workingDir),
          os.read(file),
          jVariables
        )

        reporter.printStream.flush()

        // Diagnostics returned by mdoc only cover the document itself
        // (e.g. variable substitution); compilation errors are reported
        // through the console reporter
        val diagnostics = evaluated.diagnostics().asScala.toList

        def render(d: mdi.Diagnostic) = {
          val pos = d.position()
          s"$file:${pos.startLine() + 1}:${pos.startColumn() + 1}: ${d.message()}"
        }

        diagnostics.foreach { d =>
          d.severity() match {
            case DiagnosticSeverity.Error => logger.at("ERR").logLine(render(d))
            case DiagnosticSeverity.Warning =>
              logger.at("WARN").logLine(render(d))
            case _ => logger.at("INFO").logLine(render(d))
          }
        }

        val errors =
          diagnostics
            .filter(_.severity() == DiagnosticSeverity.Error)
            .map(render) ++ reporter.errors

        if (errors.nonEmpty)
          throw SubatomicError.mdocEvaluationError(file.toString, errors)

        file -> os.temp(
          contents = evaluated.content(),
          dir = outputDir,
          suffix = ".md"
        )
      }
    }
  }

  def process(file: os.Path): os.Path = {
    processAll(Seq(file), None).head._2
  }

  private def withMdoc[A](workingDir: os.Path, reporter: MdocReporterStream)(
      f: mdi.Mdoc => A
  ): A = {
    val thread         = Thread.currentThread()
    val oldClassloader = thread.getContextClassLoader()

    thread.setContextClassLoader(classloader)

    try {
      val loaded = ServiceLoader
        .load(classOf[mdi.Mdoc], classloader)
        .iterator()
        .asScala
        .toList
        .headOption
        .getOrElse(
          throw SubatomicError(
            s"Failed to load mdoc.interfaces.Mdoc implementation from mdoc ${config.mdocVersion}"
          )
        )

      val mdoc = loaded
        .withWorkingDirectory(workingDir.toNIO)
        .withClasspath(compilerClasspath.asJava)
        .withConsoleReporter(reporter.printStream)

      try f(mdoc)
      finally mdoc.shutdown()
    } finally {
      thread.setContextClassLoader(oldClassloader)
      reporter.printStream.flush()
    }
  }

  /** mdoc expects a filename relative to its working directory */
  private def relativeFilename(file: os.Path, workingDir: os.Path): String =
    if (file.startsWith(workingDir)) file.relativeTo(workingDir).toString
    else file.last

  private def splitClasspath(cp: Option[String]): Seq[Path] =
    cp.toSeq
      .flatMap(_.split(File.pathSeparator))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(java.nio.file.Paths.get(_))

  /** Classpath mdoc itself runs on */
  private lazy val mdocRuntimeClasspath: Seq[Path] =
    mainCp ++ splitClasspath(launcherClasspath)

  /** Classpath that mdoc uses to compile the snippets */
  private lazy val compilerClasspath: Seq[Path] = {
    val scala3CP = if (config.scalaBinaryVersion == "3") mainCp else Seq.empty

    (scala3CP ++ fetchCp(config.extraDependencies) ++ splitClasspath(
      inheritedClasspath
    )).distinct
  }

  private lazy val classloader: URLClassLoader =
    MdocClassLoader.create(mdocRuntimeClasspath.map(_.toUri.toURL))

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

  private lazy val mainCp: Seq[Path] =
    Fetch()
      .addDependencies(mdocDep)
      .addDependencies(scala3Deps: _*)
      .run()
      .map(_.toPath())

  private def fetchCp(deps: Iterable[String]): Seq[Path] =
    if (deps.isEmpty) Seq.empty
    else
      Fetch()
        .addDependencies(
          deps.toSeq
            .map(DependencyParser.dependency(_, config.scalaBinaryVersion))
            .map(_.left.map(new RuntimeException(_)).toTry.get): _*
        )
        .run()
        .map(_.toPath())
}

/** Receives output of mdoc's console reporter: forwards it to the logger line
  * by line, and collects reported errors.
  *
  * mdoc reports compilation errors through the console reporter rather than
  * through the diagnostics of `EvaluatedMarkdownDocument`, so this is the only
  * way to find out that a document failed to compile. Each message reported by
  * mdoc starts with a severity prefix (`error:`, `warning:`, ...), and can span
  * multiple lines.
  */
private class MdocReporterStream(logger: Logger) extends java.io.OutputStream {
  private val buffer    = new ByteArrayOutputStream()
  private val collected = List.newBuilder[String]
  private var current   = Option.empty[StringBuilder]

  val printStream =
    new PrintStream(this, false, StandardCharsets.UTF_8.name())

  private val Ansi           = "\u001b\\[[0-9;]*m".r
  private val SeverityPrefix = "^(error|warning|info|debug):.*".r

  def errors: List[String] = synchronized {
    finishCurrent()
    collected.result()
  }

  def reset(): Unit = synchronized {
    printStream.flush()
    collected.clear()
    current = None
  }

  override def write(b: Int): Unit = synchronized {
    if (b == '\n') emit()
    else buffer.write(b)
  }

  override def flush(): Unit = synchronized {
    if (buffer.size() > 0) emit()
  }

  private def finishCurrent(): Unit = {
    current.foreach(sb => collected += sb.result())
    current = None
  }

  private def emit(): Unit = {
    val line =
      new String(buffer.toByteArray(), StandardCharsets.UTF_8).stripSuffix("\r")
    buffer.reset()

    logger.logLine(line)

    val plain = Ansi.replaceAllIn(line, "")

    if (plain.startsWith("error:")) {
      finishCurrent()
      current = Some(new StringBuilder(plain.stripPrefix("error:").trim))
    } else if (SeverityPrefix.pattern.matcher(plain).matches()) {
      finishCurrent()
    } else current.foreach(_.append("\n").append(plain))
  }
}
