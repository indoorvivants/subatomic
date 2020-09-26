package com.indoorvivants.subatomic

import scala.collection.parallel.CollectionConverters._

sealed trait SiteAsset
case class Page(content: String)     extends SiteAsset
case class CopyFile(source: os.Path) extends SiteAsset

object Site {
  def build1[Content, A1](destination: os.Path)(
      sitemap: Vector[(os.RelPath, Content)],
      a1: Function2[os.RelPath, Content, A1]
  )(assembler: Function3[os.RelPath, Content, A1, SiteAsset]) = {
    sitemap.par.foreach {
      case (relPath, content) =>
        val a1r = a1(relPath, content)

        handleAsset(
          assembler(relPath, content, a1r),
          os.Path(relPath, destination)
        )
    }

  }

  def build2[Content, A1, A2](destination: os.Path)(
      sitemap: Vector[(os.RelPath, Content)],
      a1: Function2[os.RelPath, Content, A1],
      a2: Function2[os.RelPath, Content, A2]
  )(assembler: Function4[os.RelPath, Content, A1, A2, SiteAsset]) = {
    sitemap.par.foreach {
      case (relPath, content) =>
        val a1r = a1(relPath, content)
        val a2r = a2(relPath, content)

        handleAsset(
          assembler(relPath, content, a1r, a2r),
          os.Path(relPath, destination)
        )
    }

  }

  def build3[Content, A1, A2, A3](destination: os.Path)(
      sitemap: Vector[(os.RelPath, Content)],
      a1: Function2[os.RelPath, Content, A1],
      a2: Function2[os.RelPath, Content, A2],
      a3: Function2[os.RelPath, Content, A3]
  )(assembler: Function5[os.RelPath, Content, A1, A2, A3, SiteAsset]) = {
    sitemap.par.foreach {
      case (relPath, content) =>
        val a1r = a1(relPath, content)
        val a2r = a2(relPath, content)
        val a3r = a3(relPath, content)

        handleAsset(
          assembler(relPath, content, a1r, a2r, a3r),
          os.Path(relPath, destination)
        )
    }
  }

  def build4[Content, A1, A2, A3, A4](destination: os.Path)(
      sitemap: Vector[(os.RelPath, Content)],
      a1: Function2[os.RelPath, Content, A1],
      a2: Function2[os.RelPath, Content, A2],
      a3: Function2[os.RelPath, Content, A3],
      a4: Function2[os.RelPath, Content, A4]
  )(assembler: Function6[os.RelPath, Content, A1, A2, A3, A4, SiteAsset]) = {
    sitemap.par.foreach {
      case (relPath, content) =>
        val a1r = a1(relPath, content)
        val a2r = a2(relPath, content)
        val a3r = a3(relPath, content)
        val a4r = a4(relPath, content)

        handleAsset(
          assembler(relPath, content, a1r, a2r, a3r, a4r),
          os.Path(relPath, destination)
        )
    }
  }

  private def handleAsset(ass: SiteAsset, destination: os.Path) =
    ass match {
      case Page(content) => write(content, destination)
      case CopyFile(source) =>
        os.makeDir.all(destination / os.up)

        os.copy(from = source, to = destination, replaceExisting = true)
    }

  private def write(ct: String, absPath: os.Path) = {
    os.makeDir.all(absPath / os.up)

    os.write.over(absPath, ct)
  }

}
