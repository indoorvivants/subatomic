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

sealed trait SiteAsset
case class Page(content: String)                      extends SiteAsset
case class CopyOf(source: os.Path)                    extends SiteAsset
case class CreatedFile(source: os.Path, to: SitePath) extends SiteAsset

object Site {

  def trim(content: String, len: Int = 50) =
    if (content.length > len) content.take(len - 3) + "..."
    else content

  def logHandling[T](original: T, p: os.RelPath, asset: => SiteAsset) = {
    import logger._

    val arrow = asset match {
      case _: Page        => _red("^--content-->")
      case _: CopyOf      => _red("^--copy-of-->")
      case _: CreatedFile => _red("^--created-from-->")
    }

    val rightSide = asset match {
      case Page(content) => trim(content, 50)

      case CopyOf(source)     => source.toString
      case CreatedFile(at, _) => at.toString()
    }

    val leftSide = asset match {
      case CreatedFile(_, to) => to.toString()
      case _                  => p.toString()
    }

    val origMsg = asset match {
      case _: Page =>
        List("\n    ", _bold(trim(original.toString(), 70)))
      case _ => List.empty
    }

    val msg = List(
      _blue(leftSide),
      "\n    ",
      arrow,
      " ",
      _green(
        rightSide
      )
    )

    log(msg ++ origMsg ++ List("\n"))
  }

  def build[Content](destination: os.Path)(
      sitemap: Vector[(SitePath, Content)]
  )(assembler: Function2[SitePath, Content, Iterable[SiteAsset]]) = {
    logger.logLine(
      "\nCreating site in " + logger._green(
        destination.toIO.getAbsolutePath()
      ) + "\n"
    )

    sitemap.foreach {
      case (relPath, content) =>
        handleAssets(
          relPath,
          content,
          assembler(relPath, content),
          destination
        )
    }

  }

  def build1[Content, A1](destination: os.Path)(
      sitemap: Vector[(SitePath, Content)],
      a1: Function2[SitePath, Content, A1]
  )(assembler: Function3[SitePath, Content, A1, Iterable[SiteAsset]]) = {

    logger.logLine(
      "\nCreating site in " + logger._green(
        destination.toIO.getAbsolutePath()
      ) + "\n"
    )

    sitemap.foreach {
      case (relPath, content) =>
        val a1r = a1(relPath, content)

        handleAssets(
          relPath,
          content,
          assembler(relPath, content, a1r),
          destination
        )
    }

  }

  def build2[Content, A1, A2](destination: os.Path)(
      sitemap: Vector[(SitePath, Content)],
      a1: Function2[SitePath, Content, A1],
      a2: Function2[SitePath, Content, A2]
  )(assembler: Function4[SitePath, Content, A1, A2, Iterable[SiteAsset]]) = {
    logger.logLine(
      "\nCreating site in " + logger._green(
        destination.toIO.getAbsolutePath()
      ) + "\n"
    )

    sitemap.foreach {
      case (relPath, content) =>
        val a1r = a1(relPath, content)
        val a2r = a2(relPath, content)

        handleAssets(
          relPath,
          content,
          assembler(relPath, content, a1r, a2r),
          destination
        )
    }

  }

  private def handleAssets[T](
      sp: SitePath,
      orig: T,
      assets: Iterable[SiteAsset],
      destinationFolder: os.Path
  ) = {
    val p           = sp.toRelPath
    val destination = destinationFolder / p

    assets.foreach { ass =>
      logHandling(orig, p, ass)
      ass match {
        case Page(content) => write(content, destination)
        case CopyOf(source) =>
          os.makeDir.all(destination / os.up)

          os.copy(from = source, to = destination, replaceExisting = true)
        case CreatedFile(from, to) =>
          val destination = destinationFolder / to.toRelPath

          os.makeDir.all(destination / os.up)

          os.copy(from = from, to = destination, replaceExisting = true)
      }
    }
  }

  private def write(ct: String, absPath: os.Path) = {
    os.makeDir.all(absPath / os.up)

    os.write.over(absPath, ct)
  }

}
