import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import $ivy.`com.vladsch.flexmark:flexmark-all:0.62.2`

object M {
  type Markdown = String

  def render(markdownFile: os.Path): String = {
    os.read(markdownFile).take(200)
  }

  def trueRender(markdownFile: os.Path) = {

    val parser = Parser.builder().build()

    val renderer = HtmlRenderer.builder().build()

    val document = parser.parse(os.read(markdownFile))

    renderer.render(document)
  }
}
