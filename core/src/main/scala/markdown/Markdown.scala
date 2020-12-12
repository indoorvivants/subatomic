/*
 * Copyright 2020 Anton Sviridov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package subatomic

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

  def renderToString(content: String) = {
    val document = parser.parse(content)

    renderer.render(document)
  }

}

object Markdown {
  def apply(extensions: Extension*) = new Markdown(extensions.toList)
}