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

package subatomic.builders

import java.nio.file.Files
import java.nio.file.Path

import scala.annotation.nowarn

import subatomic.builders.themes.MarkdownTheme
import subatomic.builders.themes.WithClassname

import com.indoorvivants.detective.Platform
import com.indoorvivants.detective.Platform.Arch._
import com.indoorvivants.detective.Platform.OS._

class TailwindCSS(val binary: Path) {
  def process(files: Seq[os.Path], markdownBase: MarkdownTheme): os.Path = {
    val inputCSS = s"""
    |@tailwind base;
    |@tailwind components;
    |@tailwind utilities;
    |${TailwindCSS.renderMarkdownBase(markdownBase)}
    """.trim.stripMargin
    println(inputCSS)
    val tempCss = os.temp(inputCSS)
    val outCss  = os.temp("")
    val args = Seq(
      binary.toString,
      "-i",
      tempCss.toString,
      "-o",
      outCss.toString,
      "--content",
      files.mkString(",")
    )

    os.proc(args).call(): @nowarn

    outCss
  }
}

object TailwindCSS {
  case class Config(version: String)
  object Config {
    val default: Config = Config(version = "3.2.7")
  }

  def renderMarkdownBase(base: MarkdownTheme) = {
    val applications = Vector.newBuilder[String]

    def add(query: String, cls: WithClassname) = {
      cls.className.filter(_.trim.nonEmpty).foreach { cls =>
        applications += s".markdown $query { @apply $cls}"
      }
    }

    add("p", base.Paragraph)
    add("a", base.Link)
    add("ul", base.UnorderedList.Container)
    add("ul > li", base.UnorderedList.Item)
    add("ol", base.OrderedList.Container)
    add("h1", base.Headings.H1)
    add("h2", base.Headings.H2)
    add("h3", base.Headings.H3)
    add("h4", base.Headings.H4)
    add("blockquote", base.Quote)

    "@layer components {\n" + applications.result().mkString("\n") + "\n}"
  }

  def bootstrap(config: Config, cacheDir: Path) = {

    val path =
      cacheDir.resolve(config.version).resolve(binaryName(Platform.target))
    Files.createDirectories(path.getParent())

    if (!path.toFile().exists()) {
      val url = binaryUrl(config, Platform.target)

      System.err.println(s"Downloading $url to $path")

      os.write(os.Path(path), requests.get.stream(url))

      path.toFile().setExecutable(true)

    } else if (!Files.isExecutable(path)) {
      path.toFile().setExecutable(true)
    }
    new TailwindCSS(path)

  }

  def binaryName(target: Platform.Target) = {
    val prefix = "tailwindcss"
    val ext = target.os match {
      case Platform.OS.Windows => ".exe"
      case _                   => ""
    }
    val os = target.os match {
      case Linux   => "linux"
      case MacOS   => "macos"
      case Unknown => "unknown"
      case Windows => "windows"
    }

    val arch = target.arch match {
      case Arm if target.bits == Platform.Bits.x64 => "arm64"
      case Arm                                     => "armv7"
      case Intel                                   => "x64"
    }

    s"$prefix-$os-$arch$ext"
  }

  def binaryUrl(config: Config, target: Platform.Target) = {
    s"https://github.com/tailwindlabs/tailwindcss/releases/download/v${config.version}/${binaryName(target)}"
  }
}
