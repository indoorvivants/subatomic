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
package builders

import cats.implicits._
import com.monovore.decline._

object cli {

  sealed trait TestSearchMode extends Product with Serializable
  case object Interactive     extends TestSearchMode
  case class Query(q: String) extends TestSearchMode

  sealed trait CommandConfig extends Product with Serializable

  case class BuildConfig(
      destination: os.Path,
      disableMdoc: Boolean,
      overwrite: Boolean
  ) extends CommandConfig

  case class SearchConfig(mode: TestSearchMode, debug: Boolean) extends CommandConfig

  implicit val pathArgument: Argument[os.Path] =
    Argument[String].map(s => os.Path.apply(s))

  private val debug = Opts.flag("debug", "Output debugging information").orFalse

  private val disableMdoc = Opts
    .flag(
      "disable-mdoc",
      "Don't call mdoc. This greatly speeds up things and is useful for iterating on the design"
    )
    .orFalse

  private val testSearchInteractive = Opts
    .flag(
      "interactive",
      "Drop into a CLI to test the search over your content"
    )
    .as[TestSearchMode](Interactive)

  private val testSearchQuery = Opts
    .option[String](
      "query",
      "run a query through search"
    )
    .map[TestSearchMode](Query(_))

  private val testsearchConfig =
    (testSearchInteractive.orElse(testSearchQuery), debug).mapN[CommandConfig](SearchConfig(_, _))

  private val destination = Opts
    .option[os.Path](
      "destination",
      help = "Absolute path where the static site will be generated"
    )
    .withDefault(os.temp.dir())

  private val overwrite =
    Opts.flag("force", "Overwrite files if present at destination").orFalse

  val build = Opts.subcommand("build", "Build the site")(
    (destination, disableMdoc, overwrite).mapN[CommandConfig](BuildConfig.apply)
  )

  val search = Opts.subcommand("search", "Test the generated search index")(
    testsearchConfig
  )

  val command = Command("subatomic", "static site builder")(build orElse search)
}
