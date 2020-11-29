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

package com.indoorvivants.subatomic

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

object SubatomicPlugin extends AutoPlugin {
  object autoImport {
    val subatomicInheritClasspath = settingKey[Boolean](
      "Allow subatomic to collect the classpath of this module and use it " +
        "as additional input when running mdoc"
    )

    val subatomicAddDependency = settingKey[Boolean](
      "Add subatomic as a dependency "
    )
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] =
    List(
      subatomicInheritClasspath := true,
      subatomicAddDependency := true,
      libraryDependencies ++= {
        if (subatomicAddDependency.value)
          List("com.indoorvivants" %% "subatomic" % Props.version)
        else Nil
      },
      resourceGenerators in Compile += Def.task {
        import scala.collection.mutable

        val out =
          managedResourceDirectories
            .in(Compile)
            .value
            .head / "subatomic.properties"
        val classpath = mutable.ListBuffer.empty[File]
        // Can't use fullClasspath.value because it introduces cyclic dependency between
        // compilation and resource generation.
        classpath ++= dependencyClasspath
          .in(Compile)
          .value
          .iterator
          .map(_.data)
        classpath += classDirectory.in(Compile).value

        if (subatomicInheritClasspath.value) {
          val props = new java.util.Properties()

          props.setProperty(
            "classpath",
            classpath.mkString(java.io.File.pathSeparator)
          )

          IO.write(props, "subatomic properties", out)

          List(out)
        } else Nil
      }
    )
}
