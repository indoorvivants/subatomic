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

object logger {
  import Console._

  private lazy val colors =
    System.console() != null && System.getenv().get("TERM") != null

  def _blue(s: String)  = if (!colors) s else CYAN + s + RESET
  def _red(s: String)   = if (!colors) s else RED + s + RESET
  def _green(s: String) = if (!colors) s else GREEN + s + RESET
  def _bold(s: String)  = if (!colors) s else BOLD + s + RESET

  def log(segments: List[String]) = {
    segments.foreach { seg =>
      print(seg)
    }
  }

  def logLine(string: String) = {
    println(string)
  }
}
