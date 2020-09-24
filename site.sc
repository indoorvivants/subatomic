object Site {
  def components1[Content, A1](
      sitemap: Vector[(os.RelPath, Content)],
      a1: Function2[os.RelPath, Content, A1]
  )(assembler: Function3[os.RelPath, Content, A1, String]) = {
    sitemap.foreach {
      case (relPath, content) =>
        val a1r = a1(relPath, content)

        val ct = assembler(relPath, content, a1r)

        val absPath = os.Path(relPath, os.pwd)
        val dirPath = os.makeDir.all(absPath / os.up)

        os.write.over(absPath, ct)
    }

  }

  def components2[Content, A1, A2](
      sitemap: Vector[(os.RelPath, Content)],
      a1: Function2[os.RelPath, Content, A1],
      a2: Function2[os.RelPath, Content, A2]
  )(assembler: Function2[A1, A2, String]) = {
    sitemap.foreach {
      case (relPath, content) =>
        val a1r = a1(relPath, content)
        val a2r = a2(relPath, content)

        val ct = assembler(a1r, a2r)

        val absPath = os.Path(relPath, os.pwd)
        val dirPath = os.makeDir.all(absPath / os.up)

        os.write.over(absPath, ct)
    }

  }

  def components3[Content, A1, A2, A3](
      sitemap: Vector[(os.RelPath, Content)],
      a1: Function2[os.RelPath, Content, A1],
      a2: Function2[os.RelPath, Content, A2],
      a3: Function2[os.RelPath, Content, A3]
  )(assembler: Function3[A1, A2, A3, String]) = {
    sitemap.foreach {
      case (relPath, content) =>
        val a1r = a1(relPath, content)
        val a2r = a2(relPath, content)
        val a3r = a3(relPath, content)

        val ct = assembler(a1r, a2r, a3r)

        val absPath = os.Path(relPath, os.pwd)
        val dirPath = os.makeDir.all(absPath / os.up)

        os.write.over(absPath, ct)
    }

  }
}
