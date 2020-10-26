package com.indoorvivants.subatomic

import scala.jdk.CollectionConverters._

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import com.vladsch.flexmark.util.misc.Extension

class Markdown(extensions: List[Extension]) {
  private val opts = extensions match {
    case l if l.nonEmpty =>
      new MutableDataSet()
        .set(
          Parser.EXTENSIONS,
          l.asJava
        )
    case Nil => new MutableDataSet()
  }

  private val parser = Parser.builder(opts).build()

  private val renderer = HtmlRenderer.builder(opts).build()

  def renderToString(markdownFile: os.Path) = {
    val document = parser.parse(os.read(markdownFile))

    renderer.render(document)
  }

}

object Markdown {
  def apply(extensions: Extension*) = new Markdown(extensions.toList)
}
