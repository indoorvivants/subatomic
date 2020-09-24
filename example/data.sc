// ----- Data models for content
sealed trait Tag
case object Scala extends Tag
case class ScalaMdoc(dependencies: List[String]) extends Tag
case class tg(s: String) extends Tag {
  override def toString = s
}

sealed trait Content
case class BlogPost(
    title: String,
    slug: String,
    tags: Set[Tag] = Set.empty
) extends Content {
  def file(wd: os.Path): os.Path = {
    wd / "content" / "blog" / s"$slug.md"
  }
}

case class MarkdownPage(title: String, file: os.Path) extends Content
case class StaticFile(file: os.Path) extends Content

// an extractor to help identify mdoc-based posts
object MdocBlogPost {

  def unapply(c: Content): Option[(BlogPost, ScalaMdoc)] = {
    c match {
      case bp: BlogPost =>
        bp.tags.collectFirst {
          case sm: ScalaMdoc => bp -> sm
        }
      case _ => None
    }
  }
}

//------- Data models for the site

type Title = String
type URL = String
type Selected = Boolean
case class Navigation(links: List[(Title, URL, Selected)])

//------ Content itself

def blogs(SiteRoot: os.RelPath, ContentRoot: os.Path) =
  Vector(
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
    BlogPost(
      "Test mdoc blog",
      "test-mdoc",
      tags = Set(Scala, ScalaMdoc(List("org.typelevel::cats-effect:2.1.4")))
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
  os.walk(ContentRoot / "assets").map { path =>
    SiteRoot / path.relativeTo(ContentRoot) -> StaticFile(path)
  }
}

def Content(SiteRoot: os.RelPath, ContentRoot: os.Path) =
  blogs(SiteRoot, ContentRoot) ++ pages(SiteRoot, ContentRoot) ++ statics(
    SiteRoot,
    ContentRoot
  )
