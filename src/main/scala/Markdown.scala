package com.indoorvivants.subatomic

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser

object Markdown {
  def renderToString(markdownFile: os.Path) = {

    val parser = Parser.builder().build()

    val renderer = HtmlRenderer.builder().build()

    val document = parser.parse(os.read(markdownFile))

    renderer.render(document)
  }

  def renderToScalatags(markdownFile: os.Path) = {
    scalatags.Text.all.raw(renderToString(markdownFile))
  }
}
