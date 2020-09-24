import $file.markdown

import $ivy.`com.lihaoyi::scalatags:0.9.1`

import scalatags.Text.all._
import scalatags.Text.TypedTag

object R {
    def renderMarkdown(md: markdown.M.Markdown): TypedTag[String] = div(raw(md))
}
