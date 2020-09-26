package example

// ----- Data models for content
sealed trait Tag
case object Scala                                extends Tag
case class ScalaMdoc(dependencies: List[String]) extends Tag
case class tg(s: String) extends Tag {
  override def toString = s
}

sealed trait Content

trait SlugBased {
  def slug: String
  def file(wd: os.Path): os.Path = {
    wd / "content" / "blog" / s"$slug.md"
  }
}

case class BlogPost(
    val title: String,
    val slug: String,
    val tags: Set[Tag] = Set.empty
) extends Content
    with SlugBased
case class ScalaBlogPost(
    title: String,
    slug: String,
    tags: Set[Tag],
    dependencies: List[String] = Nil
) extends Content
    with SlugBased
case class ScalaJSBlogPost(
    title: String,
    slug: String,
    tags: Set[Tag],
    dependencies: List[String] = Nil
) extends Content
    with SlugBased
case class MarkdownPage(title: String, file: os.Path) extends Content
case class StaticFile(file: os.Path)                  extends Content

//------- Data models for the site
import Navigation._
case class Navigation(links: List[(Title, URL, Selected)])

object Navigation {
  type Title    = String
  type URL      = String
  type Selected = Boolean
}

//------ Content itself
object Data {
  def blogs(SiteRoot: os.RelPath) =
    Vector[Content with SlugBased](
      BlogPost(
        "Google search history analysis",
        "google-search-history-analysis",
        tags = Set(
          tg("R"),
          tg("python"),
          tg("data-analysis"),
          tg("stocks"),
          tg("ggplot2")
        )
      ),
      BlogPost(
        "Visualising timeseries: stocks data and global trends",
        "visualising-real-world-time-series",
        tags = Set(tg("R"), tg("python"), tg("data-analysis"))
      ),
      ScalaBlogPost(
        "Test mdoc blog",
        "test-mdoc",
        tags = Set(tg("scala"))
      ),
      ScalaJSBlogPost(
        "Test mdoc blog",
        "test-mdoc-js",
        tags = Set(tg("scala"))
      )
    ).map { blogPost =>
      SiteRoot / "blog" / s"${blogPost.slug}.html" -> blogPost
    }

  def pages(SiteRoot: os.RelPath, ContentRoot: os.Path) =
    Vector(
      SiteRoot / "index.html" -> MarkdownPage(
        "Home",
        ContentRoot / "content" / "pages" / "index.md"
      ),
      SiteRoot / "cv.html" -> MarkdownPage(
        "CV",
        ContentRoot / "content" / "pages" / "cv.md"
      )
    )

  def statics(SiteRoot: os.RelPath, ContentRoot: os.Path) = {
    os.walk(ContentRoot / "assets").filter(_.toIO.isFile()).map { path =>
      SiteRoot / path.relativeTo(ContentRoot) -> StaticFile(path)
    }
  }

  def Content(SiteRoot: os.RelPath, ContentRoot: os.Path) =
    blogs(SiteRoot) ++ pages(SiteRoot, ContentRoot) ++ statics(
      SiteRoot,
      ContentRoot
    )
}
