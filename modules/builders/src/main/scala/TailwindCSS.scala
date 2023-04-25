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
import com.indoorvivants.yank.tools

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

  def bootstrap(config: Config) = new TailwindCSS(
    tools.TailwindCSS.bootstrap(
      tools.TailwindCSS.Config(version = config.version)
    )
  )

}
