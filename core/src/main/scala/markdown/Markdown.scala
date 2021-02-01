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

import com.vladsch.flexmark.ast.FencedCodeBlock
import com.vladsch.flexmark.ast.Heading
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterNode
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html.renderer.HeaderIdGenerator
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Document
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.ast.TextContainer
import com.vladsch.flexmark.util.data.MutableDataSet
import com.vladsch.flexmark.util.misc.Extension

class Markdown(extensions: List[Extension]) {
  private val opts = extensions match {
    case _ :: _ =>
      new MutableDataSet()
        .set(
          Parser.EXTENSIONS,
          extensions.asJava
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
  import Markdown.Section

  def extractMarkdownSections(documentTitle: String, baseUrl: String, p: os.Path): Vector[Section] = {
    type Result = Either[
      (String, String),
      String
    ]

    val document = read(p)

    val generator = new HeaderIdGenerator.Factory().create()

    generator.generateIds(document)

    val sect = recursiveCollect[Result](document) {
      case head: Heading =>
        Collector.Collect(Seq(Left(head.getText().toStringOrNull() -> head.getAnchorRefId())))
      case t: TextContainer =>
        Collector.Collect(Seq(Right(t.getChars().toStringOrNull())))
      case _: FencedCodeBlock | _: YamlFrontMatterNode => Collector.Skip
      case _: Node                                     => Collector.Recurse()
    }

    var currentSection: String => Section = Section(documentTitle, None, _)

    val sections = ArrayBuffer[Section]()

    val collectedText = new StringBuilder

    sect.foreach {
      case Left((title, id)) =>
        sections.append(currentSection(collectedText.result()))
        collectedText.clear()
        currentSection = Section(title, Some(baseUrl + s"#$id"), _)
      case Right(content) =>
        collectedText.append(content + "\n")
    }

    if (collectedText.nonEmpty) sections.append(currentSection(collectedText.result()))

    sections.toVector
  }
}

object Markdown {
  case class Section(title: String, url: Option[String], text: String)
  def apply(extensions: Extension*) = new Markdown(extensions.toList)
}
