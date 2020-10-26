package com.indoorvivants.subatomic

sealed trait SiteAsset
case class Page(content: String)                      extends SiteAsset
case class CopyOf(source: os.Path)                    extends SiteAsset
case class CreatedFile(source: os.Path, to: SitePath) extends SiteAsset

object Site {

  object logger {
    import Console._

    private lazy val colors =
      System.console() != null && System.getenv().get("TERM") != null

    def blue(s: String)  = if (!colors) s else CYAN + s + RESET
    def red(s: String)   = if (!colors) s else RED + s + RESET
    def green(s: String) = if (!colors) s else GREEN + s + RESET
    def bold(s: String)  = if (!colors) s else BOLD + s + RESET
  }

  def trim(content: String, len: Int = 50) =
    if (content.length > len) content.take(len - 3) + "..."
    else content

  def logHandling[T](original: T, p: os.RelPath, cont: SiteAsset) = {
    val arrow = cont match {
      case _: Page        => logger.red("^--content-->")
      case _: CopyOf      => logger.red("^--copy-of-->")
      case _: CreatedFile => logger.red("^--created-from-->")
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
      logger.blue(leftSide) + "\n    " + arrow + " " + logger.green(rightSide)

    cont match {
      case _: Page =>
        println(
          msg + "\n" + "    " + logger.bold(trim(original.toString(), 70))
        )
      case _ => println(msg)
    }
  }

  def build[Content](destination: os.Path)(
      sitemap: Vector[(SitePath, Content)]
  )(assembler: Function2[SitePath, Content, Iterable[SiteAsset]]) = {
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
