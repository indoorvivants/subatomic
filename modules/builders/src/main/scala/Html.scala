package subatomic.builders

import scalatags.Text.all._
import scalatags.Text.TypedTag

object Html {

  def renderTOC(toc: TOC, theme: MarkdownTheme) = {

    def whoosh(t: MarkdownTheme => WithClassname) =
      t(theme).className.map(cls := _)

    div(
      whoosh(_.TableOfContents.Container), {
        def render(toc: TOC): Option[TypedTag[String]] = {
          if (toc.level.nonEmpty)
            Some(
              ul(
                whoosh(_.TableOfContents.List),
                toc.level.map { case (h, nest) =>
                  li(
                    a(
                      whoosh(_.TableOfContents.Link),
                      href := s"#${h.anchorId}",
                      h.title
                    ),
                    render(nest)
                  )
                }
              )
            )
          else None
        }
        render(toc)
      }
    )
  }

}
