package com.indoorvivants.subatomic

import scala.collection.parallel.immutable.ParVector

sealed trait SiteAsset
case class Page(content: String)                    extends SiteAsset
case class CopyOf(source: os.Path)                  extends SiteAsset
case class CreatedFile(at: os.Path, to: os.RelPath) extends SiteAsset

object Site {

  object logger {
    def blue(s: String)  = fansi.Color.Cyan(s).render
    def red(s: String)   = fansi.Color.Red(s).render
    def green(s: String) = fansi.Color.Green(s).render
    def bold(s: String)  = fansi.Bold.On(s).render
  }

  def trim(content: String, len: Int = 50) =
    if (content.length > len) content.take(len - 3) + "..."
    else content

  def logHandling[T](original: T, p: os.RelPath, cont: SiteAsset) = {
    val arrow = cont match {
      case _: Page        => logger.red("<--write--")
      case _: CopyOf      => logger.red("<--copy-of--")
      case _: CreatedFile => logger.red("<--created-from--")
    }

    val rightSide = cont match {
      case Page(content) => trim(content, 50)

      case CopyOf(source)     => source.toString
      case CreatedFile(at, _) => at.toString()
    }

    val leftSide = cont match {
      case CreatedFile(_, to) => to.toString()
      case _                  => p.toString()
    }

    val msg =
      logger.blue(leftSide) + " " + arrow + " " + logger.green(rightSide)

    cont match {
      case _: Page =>
        println(
          msg + "\n" + "    " + logger.bold(trim(original.toString(), 70))
        )
      case _ => println(msg)
    }
  }

  def build1[Content, A1](destination: os.Path)(
      sitemap: Vector[(os.RelPath, Content)],
      a1: Function2[os.RelPath, Content, A1]
  )(assembler: Function3[os.RelPath, Content, A1, Iterable[SiteAsset]]) = {
    ParVector(sitemap: _*).foreach {
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

  private def handleAssets[T](
      p: os.RelPath,
      orig: T,
      assets: Iterable[SiteAsset],
      destinationFolder: os.Path
  ) = {
    val destination = destinationFolder / p

    assets.foreach { ass =>
      logHandling(orig, p, ass)
      ass match {
        case Page(content) => write(content, destination)
        case CopyOf(source) =>
          os.makeDir.all(destination / os.up)

          os.copy(from = source, to = destination, replaceExisting = true)
        case CreatedFile(from, to) =>
          val destination = destinationFolder / to

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
