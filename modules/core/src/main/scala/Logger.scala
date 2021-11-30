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

class Logger(val impl: String => Unit) extends Colors {
  def _print(s: String)   = impl(s)
  def _println(s: String) = impl(s + "\n")

  def at(name: String) = {
    new Logger(s => impl(s"[$name] $s"))
  }

  def log(segments: List[String]) = {
    segments.foreach { seg =>
      _print(seg)
    }
  }

  def logLine(string: String) = {
    _println(string)
  }
}

object Logger extends Colors {
  def default = new Logger(print)

  def nop = new Logger(_ => ())
}

trait Colors {
  import Console._

  private lazy val colors = true
  // System.console() != null && System.getenv().get("TERM") != null

  def _blue(s: String)  = if (!colors) s else CYAN + s + RESET
  def _red(s: String)   = if (!colors) s else RED + s + RESET
  def _green(s: String) = if (!colors) s else GREEN + s + RESET
  def _bold(s: String)  = if (!colors) s else BOLD + s + RESET

  def _redLines(s: String) = if (!colors) s else s.linesIterator.map(_red).mkString("\n")

}
