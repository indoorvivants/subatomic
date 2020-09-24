package com.indoorvivants.subatomic

sealed trait SiteAsset
case class Page(content: String) extends SiteAsset
case class CopyFile(source: os.Path) extends SiteAsset

object Site {
  def build1[Content, A1](destination: os.Path)(
      sitemap: Vector[(os.RelPath, Content)],
      a1: Function2[os.RelPath, Content, A1]
  )(assembler: Function3[os.RelPath, Content, A1, SiteAsset]) = {
    sitemap.foreach {
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
  )(assembler: Function2[A1, A2, SiteAsset]) = {
    sitemap.foreach {
      case (relPath, content) =>
        val a1r = a1(relPath, content)
        val a2r = a2(relPath, content)

        val ct = assembler(a1r, a2r)

        // write(ct, os.Path(relPath, destination))
    }

  }

  def build3[Content, A1, A2, A3](destination: os.Path)(
      sitemap: Vector[(os.RelPath, Content)],
      a1: Function2[os.RelPath, Content, A1],
      a2: Function2[os.RelPath, Content, A2],
      a3: Function2[os.RelPath, Content, A3]
  )(assembler: Function3[A1, A2, A3, SiteAsset]) = {
    sitemap.foreach {
      case (relPath, content) =>
        val a1r = a1(relPath, content)
        val a2r = a2(relPath, content)
        val a3r = a3(relPath, content)

        val ct = assembler(a1r, a2r, a3r)

        // write(ct, os.Path(relPath, destination))
    }

  }

  private def handleAsset(ass: SiteAsset, destination: os.Path) = ass match {
      case Page(content) => write(content, destination)
      case CopyFile(source) =>
        val dirPath = os.makeDir.all(destination / os.up)
        os.copy(from = source, to = destination, replaceExisting = true)
  }

  private def write(ct: String, absPath: os.Path) = {
    val dirPath = os.makeDir.all(absPath / os.up)

    os.write.over(absPath, ct)
  }

}
