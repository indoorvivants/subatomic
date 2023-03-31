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

import com.indoorvivants.detective.Platform
import com.indoorvivants.detective.Platform.Arch._
import com.indoorvivants.detective.Platform.OS._

class TailwindCSS(val binary: Path) {
  def process(files: Seq[os.Path], extraCSS: String) = {
    val inputCSS = s"""
    |@tailwind base;
    |@tailwind components;
    |@tailwind utilities;
    |@layer components {\n$extraCSS\n}
    """.trim.stripMargin

    val cssFile = os.temp(
      contents = inputCSS,
      prefix = "subatomic-tailwindcss-input-",
      deleteOnExit = true
    )
    val args = Seq(
      binary.toString,
      "-i",
      cssFile.toString,
      "--content",
      files.mkString(","),
      "--minify"
    )

    os.proc(args).call().out.text()

  }
}

object TailwindCSS {
  case class Config(version: String)
  object Config {
    val default: Config = Config(version = "3.2.7")
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
