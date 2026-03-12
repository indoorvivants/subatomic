package subatomic.builders
import subatomic.builders.SiteKind.Library
import subatomic.builders.SiteKind.Blog

sealed trait SiteKind extends Product with Serializable {
  def label = this match {
    case Library => "library"
    case Blog    => "blog"
  }
}
object SiteKind {
  case object Library extends SiteKind
  case object Blog    extends SiteKind
}
