package example

import com.indoorvivants.subatomic._

object Blog {
  import Navigation._
// How sidebar navigation should behave depending on the page
  def NavigationState(
      content: Vector[(os.RelPath, Content)]
  ): Function2[os.RelPath, Content, Navigation] = {
    val titles = content.collect {
      case (rp, bp: BlogPost) =>
        rp.toString() -> ("Blog: " + bp.title)
      case (rp, bp: ScalaBlogPost) =>
        rp.toString() -> ("Blog: " + bp.title)
      case (rp, bp: ScalaJSBlogPost) =>
        rp.toString() -> ("Blog: " + bp.title)
      case (rp, MarkdownPage(title, _)) => rp.toString() -> title
    }.toList

    val titlePlusContent: List[((Title, URL), Content)] =
      titles.zip(content.map(_._2))

    (_, content) => {
      Navigation(titlePlusContent.map {
        case ((rp, contentTitle), c) =>
          (contentTitle, "/" + rp.toString, content == c)
      })
    }
  }

// Actually putting everything together
  def buildSite(pwd: os.Path, siteBase: os.RelPath, destination: os.Path) = {
    val mdocProc   = new MdocProcessor()
    val mdocJsProc = new MdocJsProcessor()

    val content = Data.Content(SiteRoot = siteBase, ContentRoot = pwd)

    ammonite.ops.rm(destination)
    os.makeDir.all(destination)

    Site.build1[Content, Navigation](destination)(
      content,
      NavigationState(content)
    ) {
      // Rendering a mdoc-based post is a bit more involved
      case (_, bp: ScalaBlogPost, navigation) =>
        Page(
          Template
            .BlogPage(
              navigation,
              bp.title,
              bp.tags.map(_.toString()),
              Template.RawHTML(
                Markdown.renderToString(
                  mdocProc.process(pwd, bp.file(pwd), bp.dependencies)
                )
              )
            )
            .render
        )

      case (_, bp: ScalaJSBlogPost, navigation) =>
        Page(
          Template
            .BlogPage(
              navigation,
              bp.title,
              bp.tags.map(_.toString()),
              Template.RawHTML(
                Markdown.renderToString(
                  mdocJsProc.process(pwd, bp.file(pwd), bp.dependencies)
                )
              )
            )
            .render
        )

      // Rendering non-mdoc-based posts is simpler
      case (_, bp @ BlogPost(title, _, tags), navigation) =>
        Page(
          Template
            .BlogPage(
              navigation,
              title,
              tags.map(_.toString()),
              Template.RawHTML(Markdown.renderToString(bp.file(pwd)))
            )
            .render
        )

      // Rendering regular pages is even simpler
      case (_, MarkdownPage(_, file), navigation) =>
        Page(
          Template
            .Page(navigation, Template.RawHTML(Markdown.renderToString(file)))
            .render
        )

      case (_, sf: StaticFile, _) =>
        CopyFile(sf.file)
    }
  }

  def main(args: Array[String]) = {
    val pwd = os.Path(args(0))
    val out = os.Path(args(1))
    buildSite(pwd, os.RelPath("."), out)
  }

}
