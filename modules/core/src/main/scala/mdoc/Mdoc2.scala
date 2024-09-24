package subatomic

import mdoc.interfaces._
import coursier._
import coursier.core.MinimizedExclusions
import coursier.parse.DependencyParser
import os.ProcessOutput
import java.util.ServiceLoader
import scala.jdk.CollectionConverters._
import java.util.Properties

trait Mdoc {
  def processAll(
      files: Seq[os.Path],
      pwd: Option[os.Path]
  ): Seq[(os.Path, os.Path)]

  def process(file: os.Path): os.Path = {
    processAll(Seq(file), None).head._2
  }
}

object Mdoc {
  def apply(
      config: MdocConfiguration = MdocConfiguration.default,
      logger: scribe.Logger = scribe.Logger.root
  ): Mdoc =
    new MdocImpl(config, logger)
}

private[subatomic] class MdocImpl(
    config: MdocConfiguration = MdocConfiguration.default,
    logger: scribe.Logger
) extends Mdoc {

// + inheritedClasspath

  override def processAll(
      files: Seq[os.Path],
      pwd: Option[os.Path]
  ): Seq[(os.Path, os.Path)] = {
    val workingDir = pwd.getOrElse(os.pwd).toNIO
    val mdocApi = ServiceLoader
      .load(classOf[mdoc.interfaces.Mdoc], classloader)
      .iterator()
      .next()
      .withWorkingDirectory(workingDir)
      .withClasspath(extraCP.toList.asJava)

    val jVariables = config.variables.asJava

    val result = Seq.newBuilder[(os.Path, os.Path)]

    files.foreach { rawFile =>
      val md = mdocApi.evaluateMarkdownDocument(
        rawFile.toNIO.relativize(workingDir).toString(),
        os.read(rawFile),
        jVariables
      )
      val tmpFile = os.temp(md.content())

      result.addOne(rawFile -> tmpFile)
    }

    mdocApi.shutdown()

    result.result()

  }

  lazy val classloader =
    MdocClassLoader.create(mainCp.toArray.map(_.toUri.toURL))

  lazy val props = {
    val path        = "subatomic.properties"
    val classloader = this.getClass.getClassLoader
    val props       = new Properties()

    Option(classloader.getResourceAsStream(path)) match {
      case Some(stream) =>
        props.load(stream)
      case None =>
        scribe.error(s"failed to load $path")
    }

    props
  }

  private lazy val inheritedClasspath: Option[String] =
    if (config.inheritClasspath) {
      Option(props.getProperty(s"classpath.${config.group}"))
    } else None

  private lazy val launcherClasspath: Option[String] =
    if (config.inheritClasspath)
      Option(props.getProperty(s"launcherClasspath.${config.group}"))
    else None

  private lazy val inheritedVariables: Map[String, String] =
    if (config.inheritVariables) {
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

  private lazy val extraCP = {
    val scala3CP = if (config.scalaBinaryVersion == "3") mainCp else Seq.empty

    scala3CP ++ fetchCp(config.extraDependencies)
  }

  private val scala3Deps =
    if (config.scalaBinaryVersion == "3")
      Seq(
        simpleDep("org.scala-lang", "scala3-library_3", config.scalaVersion),
        simpleDep("org.scala-lang", "scala3-compiler_3", config.scalaVersion),
        simpleDep("org.scala-lang", "tasty-core_3", config.scalaVersion)
      )
    else Seq.empty

  private lazy val mainCp = {
    Fetch()
      .addDependencies(mdocDep)
      .addDependencies(scala3Deps: _*)
      .run()
      .map(_.toPath())
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

  private def simpleDep(org: String, artifact: String, version: String) =
    Dependency(
      Module(
        Organization(org),
        ModuleName(artifact)
      ),
      version
    )

  private def fetchCp(deps: Iterable[String]) = {
    Fetch()
      .addDependencies(
        deps.toSeq
          .map(DependencyParser.dependency(_, config.scalaBinaryVersion))
          .map(_.left.map(new RuntimeException(_)).toTry.get): _*
      )
      .run()
      .map(_.toPath())
  }

}
