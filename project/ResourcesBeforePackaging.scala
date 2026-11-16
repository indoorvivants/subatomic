import sbt._
import sbt.Keys._

/** Makes packaging wait for `copyResources`, in every project.
  *
  * In sbt 2.1, `packageBin / mappings` lists everything under `classDirectory`
  * but doesn't depend on `copyResources`, which copies resources into that
  * same directory. `IO.copyFile` writes `<file>.<random>.tmp` and then renames
  * it, so when something else runs `copyResources` concurrently with packaging,
  * the listing can pick up the temporary file, which is gone by the time it's
  * read:
  *
  * {{{
  * file referenced by the build does not exist:
  *   .../subatomic-search-frontend-pack/classes/search.js.da9dcc22.tmp
  * }}}
  *
  * sbt-scalafix triggers exactly that: `scalafix` depends on `compile` and
  * `copyResources`, so `...@platform=jvm/scalafix` copies a project's
  * resources while projects depending on it package it.
  */
object ResourcesBeforePackaging extends AutoPlugin {
  override def trigger  = allRequirements
  override def requires = plugins.JvmPlugin

  override def projectSettings: Seq[Setting[?]] =
    Seq(Compile, Test).flatMap { config =>
      inConfig(config)(
        packageBin / mappings := (packageBin / mappings)
          .dependsOn(copyResources)
          .value
      )
    }
}
