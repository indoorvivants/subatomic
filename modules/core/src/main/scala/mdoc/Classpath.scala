package subatomic

import scala.annotation.nowarn
import coursier.Fetch
import coursier.core.Dependency
import coursier.parse.DependencyParser
import os.ProcessOutput
import coursier.core.MinimizedExclusions
import coursier.core.Organization
import coursier.core.ModuleName
import subatomic.Classpath.Dep
import subatomic.Classpath.Path

private[subatomic] case class Classpath(deps: List[Classpath.Item]) {
  def `+`(other: Classpath.Item) = copy(other :: deps)
  def `++`(other: Classpath)     = Classpath(deps ++ other.deps)
  // lazy val resolutions = {
  //   val fetched = fetchCp(deps.collect { case Classpath.Dep(d) => d })
  // }
  private def fetchCp(
      deps: Iterable[(String, Set[(String, String)])],
      config: MdocConfiguration
  ) = {
    Fetch()
      .addDependencies(
        deps.toSeq
          .map(d =>
            DependencyParser
              .dependency(d._1, config.scalaBinaryVersion)
              .left
              .map(new RuntimeException(_))
              .toTry
              .get
              .withMinimizedExclusions(MinimizedExclusions(d._2.map(wrap)))
          ): _*
      )
      .run()
      .map(_.getAbsolutePath())
  }
  private def wrap(s: (String, String)) = Organization(s._1) -> ModuleName(s._2)
  def render(config: MdocConfiguration): String = {
    val fetched =
      fetchCp(deps.collect { case Classpath.Dep(d, excl) => d -> excl }, config)

    val pths = deps.collect { case Classpath.Path(p) =>
      p.toIO.getAbsolutePath()
    }

    (fetched ++ pths).mkString(":")
  }

  def toStringPretty(name: String) = {
    val sb              = new StringBuilder
    def line(s: String) = sb.append(s + "\n")
    line(s"Classpath ($name):")

    deps.foreach {

      case Dep(coord, exclusions) =>
        val excluding = if (exclusions.nonEmpty) " excluding:" else ""
        line(s"  $coord$excluding")
        exclusions.toList.sorted.foreach { case (org, m) =>
          line(s"    - $org:$m")
        }

      case Path(path) => line(s"  path: $path")

    }

    sb.result()
  }
}

object Classpath {
  sealed trait Item {
    def cp = Classpath(List(this))
  }
  case class Dep(coord: String, exclusions: Set[(String, String)] = Set.empty)
      extends Item
  case class Path(path: os.Path) extends Item

  def empty = Classpath(Nil)

  def dependencies(s: String*) = Classpath(s.map(Dep(_)).toList)
}

