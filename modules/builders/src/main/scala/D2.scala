package subatomic.builders

import com.indoorvivants.detective.Platform
import java.nio.file.Files

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
