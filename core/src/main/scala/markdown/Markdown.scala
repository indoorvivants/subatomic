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

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

import com.vladsch.flexmark.ast.Heading
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Document
import com.vladsch.flexmark.util.ast.Node
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

  def renderToString(markdownFile: os.Path): String = {
    val document = parser.parse(os.read(markdownFile))

    renderer.render(document)
  }

  def renderToString(content: String): String = {
    val document = parser.parse(content)

    renderer.render(document)
  }

  def renderToString(document: Document): String = {
    renderer.render(document)
  }

  def read(markdownFile: os.Path): Document = {
    read(os.read(markdownFile))
  }

  def read(content: String): Document = parser.parse(content)

  def extractHeaders(content: String) = {
    val parsed = parser.parse(content)

    parsed.getChildren().asScala.toVector.collect {
      case head: Heading =>
        println(head.getLevel() -> head.getText().toStringOrNull())
      case other => println(other)

    }
  }

  def collect[A](content: String)(f: PartialFunction[Node, A]): Vector[A] = {
    val parsed = parser.parse(content)

    parsed.getChildren().asScala.toVector.collect(f)
  }

  sealed trait Collector[+A]

  object Collector {
    case class Collect[A](values: Seq[A])         extends Collector[A]
    case class Recurse(node: Option[Node] = None) extends Collector[Nothing]
    case object Skip                              extends Collector[Nothing]
  }

  def recursiveCollect[A](parsed: Document)(f: PartialFunction[Node, Collector[A]]): Vector[A] = {
    @tailrec
    def go(node: Node, acc: ArrayBuffer[A], rem: ArrayBuffer[Node]): ArrayBuffer[A] = {

      val result = f.lift(node)

      result.foreach {
        case Collector.Recurse(Some(otherNode)) => rem.prependAll(otherNode.getChildren().asScala)
        case Collector.Recurse(None)            => rem.prependAll(node.getChildren().asScala)
        case Collector.Collect(values)          => acc.appendAll(values)
        case Collector.Skip                     => ()
      }

      if (rem.isEmpty) acc
      else go(rem.head, acc, rem.tail)
    }

    go(parsed, new ArrayBuffer[A], new ArrayBuffer[Node]).toVector

  }

  def recursiveCollect[A](content: String)(f: PartialFunction[Node, Collector[A]]): Vector[A] =
    recursiveCollect(parser.parse(content))(f)
}

object Markdown {
  def apply(extensions: Extension*) = new Markdown(extensions.toList)
}
