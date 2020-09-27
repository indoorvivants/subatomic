package example

// ----- Data models for content
case class Tag(s: String) {
  override def toString = s
}

object tg {
  def apply(s: String) = new Tag(s)
}

sealed trait Content

trait HasTitle {
  def title: String
}

trait SlugBased extends HasTitle {
  def title: String
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
case class ScalaJSBlogPost(
    title: String,
    slug: String,
    tags: Set[Tag],
    dependencies: List[String] = Nil
) extends Content
    with SlugBased
case class ScalaBlogPost(
    title: String,
    slug: String,
    tags: Set[Tag],
    dependencies: List[String] = Nil
) extends Content
    with SlugBased
case class MarkdownPage(title: String, file: os.Path)
    extends Content
    with HasTitle
case class TagPage(tag: Tag, content: Iterable[Content])
    extends Content
    with HasTitle {
  def title = s"Posts with tag $tag"
}
case class StaticFile(file: os.Path) extends Content

//------- Data models for the site
import Navigation._
case class Navigation(
    links: List[(Title, URL, Selected)],
    tags: List[(Tag, URL)]
)

object Navigation {
  type Title    = String
  type URL      = String
  type Selected = Boolean
}

//------ Content itself
object Data {
  private def Blogs(SiteRoot: os.RelPath) =
    Vector[Content with SlugBased](
      BlogPost(
        "The perils of blogging",
        "the-perils-of-blogging",
        tags = Set(
          tg("python"),
          tg("blogging")
        )
      ),
      ScalaJSBlogPost(
        "Blog post using Scala.js",
        "scala-js-blog-post",
        tags = Set(tg("scala"))
      ),
      ScalaBlogPost(
        "Blog post using Scala (with external dependencies)",
        "scala-blog-post",
        tags = Set(tg("scala")),
        dependencies = List(
          "org.typelevel::cats-effect:2.1.4"
        )
      )
    ).map { blogPost =>
      SiteRoot / "blog" / s"${blogPost.slug}.html" -> blogPost
    }

  private def Pages(SiteRoot: os.RelPath, ContentRoot: os.Path): Vector[(os.RelPath, Content)] =
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

  private def Statics(SiteRoot: os.RelPath, ContentRoot: os.Path) = {
    os.walk(ContentRoot / "assets").filter(_.toIO.isFile()).map { path =>
      SiteRoot / path.relativeTo(ContentRoot) -> StaticFile(path)
    }
  }

  private def Tags(SiteRoot: os.RelPath, content: Vector[Content]) =
    content
      .flatMap {
        case c @ BlogPost(_, _, tags) => tags.toVector.map { tag => tag -> c }
        case c @ ScalaBlogPost(_, _, tags, _) =>
          tags.toVector.map { tag => tag -> c }
        case c @ ScalaJSBlogPost(_, _, tags, _) =>
          tags.toVector.map { tag => tag -> c }
        case _ => Vector.empty
      }
      .groupBy(_._1)
      .map(c => c._1 -> c._2.map(_._2))
      .map {
        case (tag, contents) =>
          SiteRoot / "tags" / s"$tag.html" -> TagPage(tag, contents)
      }

  def Content(SiteRoot: os.RelPath, ContentRoot: os.Path) = {
    val raw = Blogs(SiteRoot) ++ Pages(SiteRoot, ContentRoot) ++ Statics(
      SiteRoot,
      ContentRoot
    )

    raw ++ Tags(SiteRoot, raw.map(_._2))
  }
}
