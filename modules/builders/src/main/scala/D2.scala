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

import com.indoorvivants.detective.Platform

class D2(binary: os.Path) {
  def diagram(code: String): String = {
    os.proc(binary, "-", "-")
      .call(
        stdin = os.ProcessInput.SourceInput(code)
      )
      .out
      .text()
  }
}

object D2 {
  case class Config(version: String)
  object Config {
    val default: Config = Config(version = "0.4.0")
  }

  def bootstrap(config: Config, cacheDir: os.Path): D2 = {
    val url =
      s"https://github.com/terrastruct/d2/releases/download/v${config.version}/${binaryName(config.version, Platform.target)}.tar.gz"

    val path =
      cacheDir / config.version / "d2"

    if (!path.toIO.isFile()) {
      os.makeDir.all(path / os.up)

      System.err.println(s"Downloading $url to $path")

      val tempDir = os.temp.dir()

      os.write(tempDir / "d2.tar.gz", requests.get.stream(url))
      os.proc("tar", "zvxf", tempDir / "d2.tar.gz", "-C", tempDir).call()
      val bin =
        os.walk(tempDir).collectFirst { case p if p.last == "d2" => p }.get

      os.copy(from = bin, to = path)
      path.toIO.setExecutable(true)

    } else if (!Files.isExecutable(path.toNIO)) {
      path.toIO.setExecutable(true)
    }

    new D2(path)
  }

  def binaryName(version: String, target: Platform.Target) = {
    val prefix = "d2"
    val ext = target.os match {
      case Platform.OS.Windows => ".exe"
      case _                   => ""
    }
    import Platform.OS._
    import Platform.Arch._
    val os = target.os match {
      case Linux   => "linux"
      case MacOS   => "macos"
      case Unknown => "unknown"
      case Windows => "windows"
    }

    val arch = target.arch match {
      case Arm if target.bits == Platform.Bits.x64 => "arm64"
      case Intel                                   => "amd64"
    }

    s"$prefix-v$version-$os-$arch"
  }

}
