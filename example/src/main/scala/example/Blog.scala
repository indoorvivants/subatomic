package example

import com.indoorvivants.subatomic._

object Blog {
  // How sidebar navigation should behave depending on the page
  def NavigationState(
      content: Vector[(os.RelPath, Content)],
      absoluteLinks: Map[Content, String]
  ): Function2[os.RelPath, Content, Navigation] = {
    val titles = content.flatMap {
      case (_, _: TagPage) => None
      case (_, ct: HasTitle) =>
        Some(ct -> ct.title)
      case _ => None
    }.toList

    val tags = content.collect {
      case (_, ct @ TagPage(tg, _)) => tg -> absoluteLinks(ct)
    }.toList

    (_, currentlyDisplayedContent) => {
      Navigation(
        titles.map {
          case (content, title) =>
            (
              title,
              absoluteLinks(content),
              content == currentlyDisplayedContent
            )
        },
        tags
      )
    }
  }

  def LinksState(
      content: Vector[(os.RelPath, Content)]
  ): Function2[os.RelPath, Content, Map[Content, os.RelPath]] = {
    val calced = content.map(_.swap).toMap

    (_, _) => calced
  }

// Actually putting everything together
  def buildSite(pwd: os.Path, siteBase: os.RelPath, destination: os.Path) = {
    val mdocProc   = new MdocProcessor()
    val mdocJsProc = new MdocJsProcessor()

    val content = Data.Content(SiteRoot = siteBase, ContentRoot = pwd)
    val absoluteLinks = content
      .map(_.swap)
      .map {
        case (cont, relative) =>
          cont -> ("/" + (siteBase / relative).toString)
      }
      .toMap

    ammonite.ops.rm(destination)
    os.makeDir.all(destination)

    Site.build1(destination)(
      content,
      NavigationState(content, absoluteLinks)
    ) {
      case (_, TagPage(tag, contents), nav) =>
        val list = contents.toVector.collect {
          case cont: HasTitle =>
            cont.title -> absoluteLinks(cont)
        }
        Some(
          Page(
            Template.TagPage(tag, list, nav).render
          )
        )
      // Rendering a mdoc-based post is a bit more involved
      case (_, bp: ScalaBlogPost, navigation) =>
        Some(
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
        )

      case (rp, bp: ScalaJSBlogPost, navigation) =>
        val result = mdocJsProc.process(pwd, bp.file(pwd), bp.dependencies)
        List(
          Page(
            Template
              .BlogPage(
                navigation,
                bp.title,
                bp.tags.map(_.toString()),
                Template.RawHTML(
                  Markdown.renderToString(
                    result.mdFile
                  )
                )
              )
              .render
          ),
          CreatedFile(result.mdjsFile, rp / os.up / result.mdjsFile.last),
          CreatedFile(result.mdocFile, rp / os.up / "mdoc.js")
        )

      // Rendering non-mdoc-based posts is simpler
      case (_, bp @ BlogPost(title, _, tags), navigation) =>
        Some(
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
        )

      // Rendering regular pages is even simpler
      case (_, MarkdownPage(title, file), navigation) =>
        Some(
          Page(
            Template
              .Page(
                title,
                navigation,
                Template.RawHTML(Markdown.renderToString(file))
              )
              .render
          )
        )

      case (_, sf: StaticFile, _) =>
        Some(CopyOf(sf.file))
    }
  }

  def main(args: Array[String]) = {
    val pwd = os.Path(args(0))
    val out = os.Path(args(1))
    buildSite(pwd, os.RelPath("."), out)
  }

}
