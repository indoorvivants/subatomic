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

import scala.collection.immutable

import com.vladsch.flexmark.ast.Code
import com.vladsch.flexmark.ast.CodeBlock
import com.vladsch.flexmark.ast.FencedCodeBlock
import com.vladsch.flexmark.ast.Image
import com.vladsch.flexmark.ast.Link
import com.vladsch.flexmark.ast.Text
import com.vladsch.flexmark.html.AttributeProvider
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html.HtmlRenderer.HtmlRendererExtension
import com.vladsch.flexmark.html.IndependentAttributeProviderFactory
import com.vladsch.flexmark.html.renderer.AttributablePart
import com.vladsch.flexmark.html.renderer.LinkResolverContext
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.parser.block.NodePostProcessor
import com.vladsch.flexmark.parser.block.NodePostProcessorFactory
import com.vladsch.flexmark.util.ast.Document
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.ast.NodeTracker
import com.vladsch.flexmark.util.data.MutableDataHolder
import com.vladsch.flexmark.util.html.MutableAttributes
import com.vladsch.flexmark.util.sequence.BasedSequence

class D2Extension(resolver: D2Extension.Diagram => SitePath) {

  class Processor(document: Document) extends NodePostProcessor {
    override def process(state: NodeTracker, node: Node): Unit = {
      node match {
        case c: FencedCodeBlock =>
          val prev = node.getPrevious()
          val info = c.getInfo().toString().split(":").toList
          info match {
            case "d2" :: name :: rest =>
              val content = c
                .getChars()
                .toString()
                .linesIterator
                .toVector
                .drop(1)
                .dropRight(1)
                .mkString(System.lineSeparator())

              val diagram =
                D2Extension.Diagram(name = name, code = content, args = rest)
              val img = new Image
              img.setUrlContent(BasedSequence.of(resolver(diagram).toString()))
              node.unlink()
              prev.insertAfter(img)
              state.nodeRemoved(node)
              state.nodeAddedWithChildren(img)
            case "d2" :: _ =>
              SubatomicError.raise(
                s"Markdown block header `${c.getInfo()}` is invalid - if it starts with d2, it should have the format `d2:<name>`"
              )
            case _ =>
          }
        case _ =>
      }
    }

  }

  class Extension() extends Parser.ParserExtension {
    override def extend(parserBuilder: Parser.Builder): Unit =
      parserBuilder.postProcessorFactory(Factory())

    override def parserOptions(options: MutableDataHolder): Unit = ()
  }

  def Factory() = new NodePostProcessorFactory(
    false
  ) {
    addNodes(classOf[FencedCodeBlock])
    override def apply(document: Document): NodePostProcessor = new Processor(
      document
    )
  }

  def create() = new Extension

}

object D2Extension {
  case class Diagram(name: String, code: String, args: List[String])

  def create(resolver: Diagram => SitePath) = new D2Extension(resolver)

}
