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

import com.vladsch.flexmark.ast.Emphasis
import com.vladsch.flexmark.ast.FencedCodeBlock
import com.vladsch.flexmark.ast.Link
import com.vladsch.flexmark.ast.Paragraph
import com.vladsch.flexmark.ast.Text
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.parser.block.NodePostProcessor
import com.vladsch.flexmark.parser.block.NodePostProcessorFactory
import com.vladsch.flexmark.util.ast.Document
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.ast.NodeTracker
import com.vladsch.flexmark.util.data.MutableDataHolder
import com.vladsch.flexmark.util.sequence.BasedSequence

class SnippetExtension(
    root: os.Path
) {
  import SnippetExtension.Opt

  class Processor(document: Document) extends NodePostProcessor {
    override def process(state: NodeTracker, node: Node): Unit =
      node match {
        case c: FencedCodeBlock =>
          val prev = node.getPrevious()
          val raw  = c
            .getChars()
            .toString()
            .linesIterator
            .toVector

          val content =
            raw
              .drop(1)
              .dropRight(1)
              .mkString(System.lineSeparator())

          val blacklist = Set(os.RelPath("monorepo")).map(root / _)

          def findGitRoot(path: os.RelPath): Option[os.Path] =
            path.segments.iterator
              .scanLeft(root)(_ / _)
              .drop(1)
              .filterNot(blacklist.contains)
              .find(p => os.exists(p / ".git"))

          def githubPermalink(
              gitRoot: os.Path,
              filePath: os.Path,
              range: Option[Opt.Range]
          ): Option[String] = {
            val isDirty = os
              .proc("git", "status", "--porcelain")
              .call(cwd = gitRoot)
              .out
              .trim()
              .nonEmpty

            if (isDirty) {
              println(s"Git root $gitRoot is dirty: $isDirty")
              None
            } else {
              val originUrl = os
                .proc("git", "remote", "get-url", "origin")
                .call(cwd = gitRoot)
                .out
                .trim()

              def make(owner: String, repo: String) = {
                val commitSha = os
                  .proc("git", "rev-parse", "HEAD")
                  .call(cwd = gitRoot)
                  .out
                  .trim()
                val relativePath = filePath.relativeTo(gitRoot)
                val lineFragment = range match {
                  case Some(Opt.Range(from, Some(to))) => s"#L$from-L$to"
                  case Some(Opt.Range(from, None))     => s"#L$from"
                  case None                            => ""
                }
                Some(
                  s"https://github.com/$owner/$repo/blob/$commitSha/$relativePath$lineFragment"
                )
              }

              originUrl match {
                case s"$_@github.com/$owner/$repo.git" => make(owner, repo)
                case s"$_@github.com:$owner/$repo.git" => make(owner, repo)
                case _                                 => None
              }
            }
          }

          def embed(path: os.RelPath, options: List[Opt]): Unit = {
            val fullPath = root / path
            println(s"Embedding $fullPath with options $options")
            val range: Option[Opt.Range] = options.collectFirst {
              case o: Opt.Range => o
            }
            val content =
              options.foldLeft(os.read.lines(fullPath))((acc, opt) =>
                opt.run(acc)
              )
            val newContent    = content.mkString(System.lineSeparator())
            val permalinkNode =
              for {
                gitRoot <- findGitRoot(path)
                _ = println(gitRoot)
                link <- githubPermalink(gitRoot, os.Path(path, root), range)
                _ = println(link)
              } yield {
                val linkNode = new Link()
                linkNode.setUrl(BasedSequence.of(link))
                linkNode.setTitle(BasedSequence.of("View on GitHub"))
                val textNode = new Text(
                  BasedSequence.of(fullPath.relativeTo(gitRoot).toString)
                )
                val emphasisNode = new Emphasis()
                emphasisNode.appendChild(textNode)
                linkNode.appendChild(emphasisNode)

                val paragraphNode = new Paragraph()
                paragraphNode.appendChild(linkNode)
                paragraphNode
              }

            val newNode = new FencedCodeBlock()
            // Set the fence characters and info
            newNode.setOpeningMarker(c.getOpeningMarker())
            newNode.setInfo(c.getInfo())
            newNode.setClosingMarker(c.getClosingMarker())

            newNode.setContent(
              java.util.Collections.singletonList(BasedSequence.of(newContent))
            )

            node.unlink()
            prev.insertAfter(newNode)
            permalinkNode.foreach(p => prev.insertAfter(p))
            state.nodeRemoved(node)
            permalinkNode.foreach(p => state.nodeAddedWithDescendants(p))
            state.nodeAddedWithChildren(newNode)
          }

          content.trim() match {
            case s"EMBED:/$relativePath:$rest" =>
              val options = rest
                .split(";")
                .toList
                .map {
                  case s"range=$from-$to" =>
                    Opt.Range(from.toInt, Some(to.toInt))
                  case s"range=$from" => Opt.Range(from.toInt, None)
                  case "align"        => Opt.Align
                  case other          => sys.error(s"Unknown option $other")
                }
              embed(os.RelPath(relativePath), options)
            case s"EMBED:/$relativePath" =>
              embed(os.RelPath(relativePath), List.empty)
            case _ =>
          }

        case _ =>
      }
  }

  class Extension() extends Parser.ParserExtension {
    override def extend(parserBuilder: Parser.Builder): Unit =
      parserBuilder.postProcessorFactory(Factory())

    override def parserOptions(options: MutableDataHolder): Unit = ()
  }

  def Factory(): NodePostProcessorFactory =
    new NodePostProcessorFactory(false) {
      addNodes(classOf[FencedCodeBlock])
      override def apply(document: Document): NodePostProcessor =
        new Processor(document)
    }

  def create() = new Extension
}

object SnippetExtension {
  case class Diagram(name: String, code: String, args: List[String])

  private[subatomic] sealed trait Opt extends Product with Serializable {
    def run(lines: IndexedSeq[String]): IndexedSeq[String] = this match {
      case Opt.Range(from, to) =>
        lines.slice(from - 1, to.getOrElse(lines.length))
      case Opt.Align =>
        val firstLineIndentation =
          lines.headOption.map(_.takeWhile(_.isWhitespace).length).getOrElse(0)
        val prefix = " " * firstLineIndentation
        if (firstLineIndentation > 0)
          lines.map(line => line.stripPrefix(prefix))
        else lines
    }
  }

  private[subatomic] object Opt {
    case class Range(from: Int, to: Option[Int]) extends Opt
    case object Align                            extends Opt
  }

  def create(
      root: os.Path
  ) = new SnippetExtension(root)
}
