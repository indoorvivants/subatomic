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

import sbt.Keys._
import sbt._

object Props {
  def version: String =
    props.getProperty("subatomic.version", "0.8.0-SNAPSHOT")

  private lazy val props: Properties = {
    val props       = new Properties()
    val path        = "subatomic-plugin.properties"
    val classloader = this.getClass.getClassLoader
    Option(classloader.getResourceAsStream(path)) match {
      case Some(stream) =>
        props.load(stream)
      case None =>
        println(s"error: failed to load $path")
    }
    props
  }
}

sealed trait DocDependency extends Product with Serializable {
  def group: String
}
case class ModuleDocDependency(mid: ModuleID, group: String)          extends DocDependency
case class ClassesDocDependency(folder: File, group: String)          extends DocDependency
case class ProjectDocDependency(ref: ProjectReference, group: String) extends DocDependency
case class ThisProjectClasses(group: String)                          extends DocDependency
case class ThisProjectDependencies(group: String)                     extends DocDependency

object SubatomicPlugin extends AutoPlugin {
  object autoImport {
    val subatomicInheritClasspath = settingKey[Boolean](
      "Allow subatomic to collect the classpath of this module and use it " +
        "as additional input when running mdoc"
    )

    val subatomicCoreDependency = settingKey[Boolean](
      "Add subatomic core as a dependency (ignored if subatomicBuildersDependency is used)"
    )

    val subatomicBuildersDependency = settingKey[Boolean](
      "Add subatomic builders as a dependency "
    )

    val subatomicMdocVariables = settingKey[Map[String, String]](
      "List of variables that will be passed to mdoc when processing " +
        "markdown documents"
    )

    val subatomicDependencies = settingKey[List[DocDependency]]("")

    object Subatomic {
      def dependency(mid: ModuleID, group: String = "default"): DocDependency       = ModuleDocDependency(mid, group)
      def classes(classes: File, group: String = "default"): DocDependency          = ClassesDocDependency(classes, group)
      def project(proj: ProjectReference, group: String = "default"): DocDependency = ProjectDocDependency(proj, group)
      def thisProjectClasses(group: String)                                         = ThisProjectClasses(group)
      def thisProjectDependencies(group: String)                                    = ThisProjectDependencies(group)
    }
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] =
    List(
      subatomicInheritClasspath := true,
      subatomicCoreDependency := true,
      subatomicBuildersDependency := true,
      subatomicDependencies := List(ThisProjectClasses("default"), ThisProjectDependencies("default")),
      subatomicMdocVariables := Map("VERSION" -> version.value),
      libraryDependencies ++= {
        (
          subatomicBuildersDependency.value,
          subatomicCoreDependency.value
        ) match {
          case (true, _) =>
            List("com.indoorvivants" %% "subatomic-builders" % Props.version)
          case (_, true) =>
            List("com.indoorvivants" %% "subatomic-core" % Props.version)
          case _ => Nil
        }
      },
      resourceGenerators in Compile += Def.task {

        def getJars(mid: ModuleID) = {

          val depRes   = dependencyResolution.in(update).value
          val updc     = updateConfiguration.in(update).value
          val uwconfig = unresolvedWarningConfiguration.in(update).value
          val modDescr = depRes.wrapDependencyInModule(mid)

          depRes
            .update(
              modDescr,
              updc,
              uwconfig,
              streams.value.log
            )
            .map(_.allFiles)
            .fold(uw => throw uw.resolveException, identity)
        }

        val out =
          managedResourceDirectories
            .in(Compile)
            .value
            .head / "subatomic.properties"

        val classpath = {
          val mut = List.newBuilder[(String, File)]

          subatomicDependencies.value.collect {
            case ModuleDocDependency(mid, gr)  => mut ++= getJars(mid).map(gr -> _)
            case ClassesDocDependency(cls, gr) => mut += gr -> cls
            case ThisProjectClasses(gr)        => mut += gr -> (Compile / classDirectory).value
            case ThisProjectDependencies(gr) =>
              mut ++= dependencyClasspath
                .in(Compile)
                .value
                .iterator
                .map(file => gr -> file.data)

          }

          mut.result()
        }

        // val projs: Seq[ProjectReference] = subatomicDocDependencies.value.collect {
        //   case ProjectDocDependency(proj) => proj
        // }

        // val scp = ScopeFilter(
        //   inProjects(projs: _*),
        //   inConfigurations(Compile)
        // )

        // println(scp)

        // println(classDirectory.all(scp).value)

        //
        // [error] (plugin2_12 / Compile / compileIncremental) java.lang.IllegalArgumentException: Could not find proxy for val scp
        // : sbt.ScopeFilter.Base in List(value scp, method $anonfun$projectSettings$8, method projectSettings, object SubatomicPlu
        // gin, package subatomic, package <root>) (currentOwner= method $anonfun$projectSettings$7 )
        // [error] Total time: 2 s, completed 19 Feb 2021, 10:41:15

        val props = new java.util.Properties()

        subatomicMdocVariables.value.foreach {
          case (varName, varValue) =>
            props.setProperty(s"variable.$varName", varValue)
        }

        if (subatomicInheritClasspath.value) {

          classpath.groupBy(_._1).foreach {
            case (groupName, clsp) =>
              props.setProperty(
                s"classpath.$groupName",
                clsp.map(_._2).mkString(java.io.File.pathSeparator)
              )

              props.setProperty(
                s"launcherClasspath.$groupName",
                clsp.map(_._2).mkString(java.io.File.pathSeparator)
              )
          }

        }

        IO.write(props, "subatomic properties", out)

        List(out)
      }
    )
}
