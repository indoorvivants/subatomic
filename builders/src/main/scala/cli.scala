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
