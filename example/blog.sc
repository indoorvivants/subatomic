import $ivy.`com.indoorvivants::subatomic:0.1.0-SNAPSHOT`
import $file.template
import $file.data

import com.indoorvivants.subatomic._
import template.Template
import data._

// How sidebar navigation should behave depending on the page
def NavigationState(
    content: Vector[(os.RelPath, Content)]
): Function2[os.RelPath, Content, Navigation] = {
  val titles = content.collect {
    case (rp, BlogPost(title, file, tags)) =>
      rp.toString() -> ("Blog: " + title)
    case (rp, MarkdownPage(title, file)) => rp.toString() -> title
  }.toList

  val titlePlusContent: List[((Title, URL), Content)] =
    titles.zip(content.map(_._2))

  (relPath, content) => {
    Navigation(titlePlusContent.map {
      case ((rp, contentTitle), c) =>
        (contentTitle, "/" + rp.toString, content == c)
    })
  }
}

// Actually putting everything together
def buildSite(pwd: os.Path, siteBase: os.RelPath, destination: os.Path) = {
  val mdocProc = new MdocProcessor()

  val content = Content(SiteRoot = siteBase, ContentRoot = pwd)

  ammonite.ops.rm(destination)
  os.makeDir.all(destination)

  Site.build1[Content, Navigation](destination)(
    content,
    NavigationState(content)
  ) {
    // Rendering a mdoc-based post is a bit more involved
    case (_, MdocBlogPost(bp, sm @ ScalaMdoc(deps)), navigation) =>
      Page(
        Template
          .BlogPage(
            navigation,
            bp.title,
            bp.tags.filter(_ != sm).map(_.toString()),
            Template.RawHTML(
              Markdown.renderToString(mdocProc.process(pwd, bp.file(pwd), deps))
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
    case (_, MarkdownPage(title, file), navigation) =>
      Page(
        Template
          .Page(navigation, Template.RawHTML(Markdown.renderToString(file)))
          .render
      )

    case (_, sf: StaticFile, _) =>
      CopyFile(sf.file)
  }
}

buildSite(os.pwd, os.RelPath("."), os.pwd / "_site")
