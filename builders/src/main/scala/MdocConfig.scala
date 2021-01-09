package subatomic
package builders

case class MdocConfig(
    dependencies: List[String]
)

object MdocConfig {
  def from(attrs: Discover.YamlAttributes): Option[MdocConfig] = {
    val enabled      = attrs.optionalOne("scala-mdoc").getOrElse("false").toBoolean
    val dependencies = attrs.optionalOne("scala-mdoc-dependencies").map(_.split(",").toList).getOrElse(Nil)

    if (enabled) Some(MdocConfig(dependencies)) else None
  }
}
