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

import java.util.Collection

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

import Markdown._
import com.vladsch.flexmark.util.data.DataKey

class Markdown(
    parserExtensions: List[Extension],
    renderExtensions: List[Extension]
) {
  private val parserOpts = parserExtensions match {
    case _ :: _ =>
      new MutableDataSet()
        .set[Collection[Extension]](
          Parser.EXTENSIONS,
          parserExtensions.asJava
        )
    case Nil => new MutableDataSet()
  }
  private val renderOpts = renderExtensions match {
    case _ :: _ =>
      new MutableDataSet()
        .set[Collection[Extension]](
          Parser.EXTENSIONS,
          renderExtensions.asJava
        )
    case Nil => new MutableDataSet()
  }

  private val parser = Parser.builder(parserOpts).build()

  private val renderer = HtmlRenderer.builder(renderOpts).build()

  def renderToString(document: Document): String = {
    renderer.render(document)
  }

  def read(markdownFile: os.Path): Document = {
    val doc = read(os.read(markdownFile))
    doc.set(PathKey, Some(markdownFile))
    doc
  }

  def read(content: String): Document = {
    parser.parse(content)
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

  def recursiveCollect[A](
      parsed: Document
  )(f: PartialFunction[Node, Collector[A]]): Vector[A] = {
    @tailrec
    def go(
        node: Node,
        acc: ArrayBuffer[A],
        rem: ArrayBuffer[Node]
    ): ArrayBuffer[A] = {

      val result = f.lift(node)

      result.foreach {
        case Collector.Recurse(Some(otherNode)) =>
          rem.prependAll(otherNode.getChildren().asScala)
        case Collector.Recurse(None) =>
          rem.prependAll(node.getChildren().asScala)
        case Collector.Collect(values) => acc.appendAll(values)
        case Collector.Skip            => ()
      }

      if (rem.isEmpty) acc
      else go(rem.head, acc, rem.tail)
    }

    go(parsed, new ArrayBuffer[A], new ArrayBuffer[Node]).toVector

  }

  def recursiveCollect[A](content: String)(
      f: PartialFunction[Node, Collector[A]]
  ): Vector[A] =
    recursiveCollect(parser.parse(content))(f)
  import Markdown.Section

  def extractMarkdownHeadings(
      document: Document
  ): Vector[Header] = {

    val generator = new HeaderIdGenerator.Factory().create()

    generator.generateIds(document)

    val sect = recursiveCollect[Header](document) {
      case head: Heading =>
        Collector.Collect(
          Seq(
            Header(
              head.getText().toStringOrNull(),
              head.getLevel(),
              head.getAnchorRefId()
            )
          )
        )
      case _: FencedCodeBlock | _: YamlFrontMatterNode => Collector.Skip
      case _: Node                                     => Collector.Recurse()
    }

    sect
  }

  def extractMarkdownSections(
      documentTitle: String,
      baseUrl: String,
      p: os.Path
  ): Vector[Section] = {
    type Result = Either[
      Header,
      String
    ]

    val document = read(p)

    val generator = new HeaderIdGenerator.Factory().create()

    generator.generateIds(document)

    val sect = recursiveCollect[Result](document) {
      case head: Heading =>
        Collector.Collect(
          Seq(
            Left(
              Header(
                head.getText().toStringOrNull(),
                head.getLevel(),
                head.getAnchorRefId()
              )
            )
          )
        )
      case t: TextContainer =>
        Collector.Collect(Seq(Right(t.getChars().toStringOrNull())))
      case _: FencedCodeBlock | _: YamlFrontMatterNode => Collector.Skip
      case _: Node                                     => Collector.Recurse()
    }

    var currentSection: String => Section = Section(documentTitle, 1, None, _)

    val sections      = Vector.newBuilder[Section]
    val collectedText = new StringBuilder

    sect.foreach {
      case Left(Header(title, lev, id)) =>
        sections += currentSection(collectedText.result())
        collectedText.clear()
        currentSection = Section(title, lev, Some(baseUrl + s"#$id"), _)
      case Right(content) =>
        collectedText.append(content + "\n")
    }

    if (collectedText.nonEmpty)
      sections += currentSection(collectedText.result())

    sections.result()
  }
}

object Markdown {
  object PathKey extends DataKey("subatomic-path", Option.empty[os.Path])
  case class Header(title: String, level: Int, anchorId: String)
  case class Section(
      title: String,
      level: Int,
      url: Option[String],
      text: String
  )
  def apply(
      parserExtensions: List[Extension] = Nil,
      renderExtensions: List[Extension] = Nil
  ) = new Markdown(parserExtensions, renderExtensions)
}
