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

/** Replaces fenced code blocks of the form `EMBED:/path/to/file[:options]` with
  * the contents of that file, resolved relative to `root`.
  *
  * @param root
  *   directory that `EMBED:` paths are resolved against
  * @param ignoredGitRoots
  *   directories (relative to `root`) that must never be treated as the git
  *   repository of an embedded file, even if they contain `.git`. The first
  *   directory with `.git` on the way from `root` to the file is used for the
  *   GitHub permalink, so list umbrella checkouts here (e.g. a `monorepo` that
  *   contains other repositories) to make the lookup continue to the nested
  *   repository instead.
  * @param logger
  *   where to report non-fatal issues, such as a dirty working tree
  */
class SnippetExtension(
    root: os.Path,
    ignoredGitRoots: Set[os.RelPath] = Set.empty,
    logger: Logger = Logger.default.at("snippets")
) {
  import SnippetExtension.Opt
  import SnippetExtension.GitInfo

  private val cache = new SnippetExtension.Cache

  private val ignoredGitRootPaths = ignoredGitRoots.map(root / _)

  /** Drops all cached file contents and git state. Call at the start of a build
    * if the same extension instance is reused across builds.
    */
  def clearCache(): Unit = cache.clear()

  private def findGitRoot(path: os.RelPath): Option[os.Path] =
    cache.gitRoot(path) {
      path.segments.iterator
        .scanLeft(root)(_ / _)
        .drop(1)
        .filterNot(ignoredGitRootPaths.contains)
        .find(p => os.exists(p / ".git"))
    }

  private def gitInfo(gitRoot: os.Path): GitInfo =
    cache.gitInfo(gitRoot) {
      def git(args: String*) =
        os.proc("git" +: args).call(cwd = gitRoot).out.trim()

      val dirty = git("status", "--porcelain").nonEmpty
      if (dirty) {
        logger.logLine(
          s"Git root $gitRoot is dirty, GitHub permalinks will be omitted"
        )
        GitInfo(dirty = true, origin = "", sha = "")
      } else
        GitInfo(
          dirty = false,
          origin = git("remote", "get-url", "origin"),
          sha = git("rev-parse", "HEAD")
        )
    }

  private def githubPermalink(
      gitRoot: os.Path,
      filePath: os.Path,
      range: Option[Opt.Range]
  ): Option[String] = {
    val info = gitInfo(gitRoot)

    def make(owner: String, repo: String) = {
      val relativePath = filePath.relativeTo(gitRoot)
      val lineFragment = range match {
        case Some(Opt.Range(from, Some(to))) => s"#L$from-L$to"
        case Some(Opt.Range(from, None))     => s"#L$from"
        case None                            => ""
      }
      Some(
        s"https://github.com/$owner/$repo/blob/${info.sha}/$relativePath$lineFragment"
      )
    }

    if (info.dirty) None
    else
      info.origin match {
        case s"$_@github.com/$owner/$repo.git" => make(owner, repo)
        case s"$_@github.com:$owner/$repo.git" => make(owner, repo)
        case _                                 => None
      }
  }

  class Processor(document: Document) extends NodePostProcessor {
    override def process(state: NodeTracker, node: Node): Unit =
      node match {
        case c: FencedCodeBlock =>
          val raw = c
            .getChars()
            .toString()
            .linesIterator
            .toVector

          val content =
            raw
              .drop(1)
              .dropRight(1)
              .mkString(System.lineSeparator())

          def embed(path: os.RelPath, options: List[Opt]): Unit = {
            val fullPath                 = root / path
            val range: Option[Opt.Range] = options.collectFirst {
              case o: Opt.Range => o
            }
            val content =
              options.foldLeft(cache.lines(fullPath))((acc, opt) =>
                opt.run(acc)
              )
            val newContent    = content.mkString(System.lineSeparator())
            val permalinkNode =
              for {
                gitRoot <- findGitRoot(path)
                link    <- githubPermalink(gitRoot, fullPath, range)
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

            // insertBefore works even when the block is the first child,
            // unlike getPrevious().insertAfter(...)
            permalinkNode.foreach(p => node.insertBefore(p))
            node.insertBefore(newNode)
            node.unlink()
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

    def clearCache(): Unit = SnippetExtension.this.clearCache()
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

  private[subatomic] final case class GitInfo(
      dirty: Boolean,
      origin: String,
      sha: String
  )

  /** In-memory cache shared by all documents parsed with the same extension
    * instance.
    *
    *   - File contents are keyed by absolute path and revalidated with a cheap
    *     `stat` (mtime + size), so unchanged files are never re-read.
    *   - Git roots and git state are computed once per repository and kept
    *     until [[clear]] is called.
    */
  private final case class FileEntry(
      mtime: Long,
      size: Long,
      lines: IndexedSeq[String]
  )

  private[subatomic] final class Cache {
    import java.util.concurrent.ConcurrentHashMap

    private val files    = new ConcurrentHashMap[os.Path, FileEntry]
    private val gitRoots = new ConcurrentHashMap[os.RelPath, Option[os.Path]]
    private val gitInfos = new ConcurrentHashMap[os.Path, GitInfo]

    def lines(path: os.Path): IndexedSeq[String] = {
      val st    = os.stat(path)
      val mtime = st.mtime.toMillis
      val size  = st.size
      files
        .compute(
          path,
          (_, old) =>
            if (old != null && old.mtime == mtime && old.size == size) old
            else FileEntry(mtime, size, os.read.lines(path))
        )
        .lines
    }

    def gitRoot(rel: os.RelPath)(find: => Option[os.Path]): Option[os.Path] =
      gitRoots.computeIfAbsent(rel, _ => find)

    def gitInfo(gitRoot: os.Path)(compute: => GitInfo): GitInfo =
      gitInfos.computeIfAbsent(gitRoot, _ => compute)

    def clear(): Unit = {
      files.clear()
      gitRoots.clear()
      gitInfos.clear()
    }
  }

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
      root: os.Path,
      ignoredGitRoots: Set[os.RelPath] = Set.empty,
      logger: Logger = Logger.default.at("snippets")
  ) = new SnippetExtension(root, ignoredGitRoots, logger)
}
