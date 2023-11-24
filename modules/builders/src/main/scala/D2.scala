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
import com.indoorvivants.yank.tools

class D2(binary: os.Path) {
  def diagram(code: String, arguments: List[String] = Nil): String = {
    os.proc(Seq(binary.toString()) ++ arguments ++ Seq("-", "-"))
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
    val default: Config = Config(version = "0.6.1")
  }

  def bootstrap(config: Config): D2 =
    new D2(
      os.Path(tools.D2.bootstrap(tools.D2.Config(version = config.version)))
    )
}
