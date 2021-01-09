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
  case class Config(
      destination: os.Path,
      disableMdoc: Boolean,
      overwrite: Boolean
  )
  implicit val pathArgument: Argument[os.Path] =
    Argument[String].map(s => os.Path.apply(s))

  private val disableMdoc = Opts
    .flag(
      "disable-mdoc",
      "Don't call mdoc. This greatly speeds up things and is useful for iterating on the design"
    )
    .orFalse

  private val destination = Opts
    .option[os.Path](
      "destination",
      help = "Absolute path where the static site will be generated"
    )
    .withDefault(os.temp.dir())

  private val overwrite =
    Opts.flag("overwrite", "Overwrite files if present at destination").orFalse

  val command = Command("build site", "builds the site")(
    (destination, disableMdoc, overwrite).mapN(Config)
  )
}
