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

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration

sealed trait SiteAsset                                extends Product with Serializable
case class Page(content: String)                      extends SiteAsset
case class CopyOf(source: os.Path)                    extends SiteAsset
case class CreatedFile(source: os.Path, to: SitePath) extends SiteAsset

sealed trait Entry                                                                  extends Product with Serializable
case class Ready(path: SitePath, content: SiteAsset)                                extends Entry
case class Delayed(path: SitePath, processor: () => SiteAsset, original: String)    extends Entry
case class DelayedMany(processor: () => Map[SitePath, SiteAsset], original: String) extends Entry

case class Site[Content] private (pages: Vector[Entry], content: Iterable[(SitePath, Content)], logger: Logger) {
  private[subatomic] def addReadyAsset(path: SitePath, asset: SiteAsset) =
    copy(pages = pages :+ Ready(path, asset))

  private[subatomic] def addDelayedAsset(path: SitePath, asset: () => SiteAsset, original: String) =
    copy(pages = pages :+ Delayed(path, asset, original))

  private[subatomic] def addDelayedAssets(assets: () => Map[SitePath, SiteAsset], original: String) =
    copy(pages = pages :+ DelayedMany(assets, original))

  def add(path: SitePath, asset: SiteAsset): Site[Content] = addReadyAsset(path, asset)

  def addPage(path: SitePath, page: String): Site[Content]      = addReadyAsset(path, Page(page))
  def addCopyOf(path: SitePath, source: os.Path): Site[Content] = addReadyAsset(path, CopyOf(source))

  def addProcessed[C1 <: Content](path: SitePath, processor: Processor[C1, SiteAsset], content: C1) = {
    processor.register(content)

    addDelayedAsset(path, () => processor.retrieve(content), content.toString())
  }

  def addProcessed[C1 <: Content](processor: Processor[C1, Map[SitePath, SiteAsset]], content: C1) = {
    addDelayedAssets(() => processor.retrieve(content), content.toString())
  }

  def noLogging = copy(logger = Logger.nop)

  def changeLogger(logger: String => Unit) = copy(logger = new Logger(logger))

  def copyAll(root: os.Path, siteBase: SitePath, filter: os.Path => Boolean = _ => true): Site[Content] = {
    os.walk(root).filter(_.toIO.isFile()).filter(filter).foldLeft(this) {
      case (site, file) =>
        val relativePath = file.relativeTo(root)

        site.addCopyOf(siteBase / relativePath, file)
    }
  }

  def populate(f: (Site[Content], (SitePath, Content)) => Site[Content]) = {
    content.foldLeft(this)(f)
  }

  def buildAt(destination: os.Path, overwrite: Boolean = false): Unit = {
    logger.logLine(
      "\nCreating site in " + logger._green(
        destination.toIO.getAbsolutePath()
      ) + "\n"
    )

    val ready = pages.collect {
      case r: Ready => r
    }

    ready.foreach {
      case Ready(sitePath, asset) =>
        Site.logEntry(sitePath.toRelPath, asset, None, logger)

        writeAsset(sitePath, asset, destination, overwrite)
    }

    val delayed = Await.result(
      Future.sequence(pages.collect {
        case d @ Delayed(_, asset, _) =>
          Future { Left(d -> asset()) }
        case d @ DelayedMany(assets, _) =>
          Future { Right(d -> assets()) }
      }),
      Duration.Inf
    )

    delayed.foreach {
      case Left((Delayed(sitePath, _, original), assetResult)) =>
        Site.logEntry(sitePath.toRelPath, assetResult, Some(original), logger)
        writeAsset(sitePath, assetResult, destination, overwrite)

      case Right((DelayedMany(_, original), results)) =>
        results.foreach {
          case (sitePath, asset) =>
            Site.logEntry(sitePath.toRelPath, asset, Some(original), logger)
            writeAsset(sitePath, asset, destination, overwrite)
        }
    }

  }

  private def writeAsset(
      sp: SitePath,
      ass: SiteAsset,
      destinationFolder: os.Path,
      overwrite: Boolean
  ) = {
    val p           = sp.toRelPath
    val destination = destinationFolder / p

    if (destination.toIO.exists() && !overwrite)
      throw SubatomicError.dangerousOverwriting(destination)

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

  private def write(ct: String, absPath: os.Path) = {
    os.makeDir.all(absPath / os.up)

    os.write.over(absPath, ct)
  }

}

object Site {

  def init[Content](c: Iterable[(SitePath, Content)]) = new Site[Content](Vector.empty, c, Logger.default)

  def trim(content: String, len: Int = 50) =
    (if (content.length > len) content.take(len - 3) + "..."
     else content).replaceAll("\n", "\\\n")

  def logEntry(path: os.RelPath, asset: SiteAsset, from: Option[String] = None, logger: Logger) = {
    import logger._

    val arrow = asset match {
      case _: Page        => _red("^--content-->")
      case _: CopyOf      => _red("^--copy-of-->")
      case _: CreatedFile => _red("^--created-from-->")
    }

    val rightSide = asset match {
      case Page(content) => trim(content, 50).replace("\n", "\\n")

      case CopyOf(source)     => source.toString
      case CreatedFile(at, _) => at.toString()
    }

    val leftSide = asset match {
      case CreatedFile(_, to) => to.toString()
      case _                  => path.toString()
    }

    val indentBreak = "\n    "

    val msg = List(
      _blue(leftSide),
      indentBreak,
      arrow,
      " ",
      _green(
        rightSide
      )
    )

    val fromMsg = from.toList.flatMap { original =>
      List(indentBreak, _red("^--processed-from-->"), " ", _bold(trim(original, 80)))
    }

    log(msg ++ fromMsg ++ List("\n"))
  }
}
