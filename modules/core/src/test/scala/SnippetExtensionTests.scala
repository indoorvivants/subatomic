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

object SnippetExtensionTests extends weaver.FunSuite {

  test("cache does not re-read a file whose mtime and size are unchanged") {
    val dir   = os.temp.dir()
    val file  = dir / "a.scala"
    val cache = new SnippetExtension.Cache

    os.write(file, "aaa")
    val mtime = os.mtime(file)
    val first = cache.lines(file)

    // same size and mtime, different content: a re-read would observe "bbb"
    os.write.over(file, "bbb")
    os.mtime.set(file, mtime)
    val second = cache.lines(file)

    // size changes: must be re-read
    os.write.over(file, "cccc")
    os.mtime.set(file, mtime)
    val third = cache.lines(file)

    expect.all(
      first == Vector("aaa"),
      second == Vector("aaa"),
      third == Vector("cccc")
    )
  }

  test("cache re-reads a file when mtime changes") {
    val dir   = os.temp.dir()
    val file  = dir / "a.scala"
    val cache = new SnippetExtension.Cache

    os.write(file, "aaa")
    val mtime = os.mtime(file)
    cache.lines(file)

    os.write.over(file, "bbb")
    os.mtime.set(file, mtime + 5000)

    expect(cache.lines(file) == Vector("bbb"))
  }

  test("embeds a snippet that is the first node in the document") {
    val dir = os.temp.dir()
    os.write(dir / "code.scala", "line1\n  line2\n  line3\nline4")

    val md   = Markdown(List(SnippetExtension.create(dir).create()))
    val html = md
      .renderToString(
        md.read("```scala\nEMBED:/code.scala:range=2-3;align\n```")
      )
      .trim

    expect(html.contains("line2\nline3")) &&
    expect(!html.contains("line1")) &&
    expect(!html.contains("EMBED"))
  }

  test("same file embedded with different ranges in one document") {
    val dir = os.temp.dir()
    os.write(dir / "code.scala", "one\ntwo\nthree")

    val md   = Markdown(List(SnippetExtension.create(dir).create()))
    val html = md.renderToString(
      md.read(
        "Intro\n\n```scala\nEMBED:/code.scala:range=1\n```\n\n```scala\nEMBED:/code.scala:range=3-3\n```"
      )
    )

    expect(html.contains("one\ntwo\nthree")) &&
    expect(html.contains("<code class=\"language-scala\">three"))
  }

  private def gitRepo(dir: os.Path, repo: String): Unit = {
    def git(args: String*) = os.proc("git" +: args).call(cwd = dir)
    git("init", "-q")
    git("remote", "add", "origin", s"git@github.com:owner/$repo.git")
    git(
      "-c",
      "user.name=test",
      "-c",
      "user.email=test@example.com",
      "commit",
      "-q",
      "--allow-empty",
      "-m",
      "init"
    )
  }

  /** <root>/umbrella is a repo that contains <root>/umbrella/nested, another
    * repo holding the embedded file. Each repo ignores its own contents so both
    * working trees stay clean.
    */
  private def nestedRepos(): os.Path = {
    val root   = os.temp.dir()
    val nested = root / "umbrella" / "nested"
    os.makeDir.all(nested)
    os.write(nested / "code.scala", "hello")
    os.write(nested / ".gitignore", "*")
    os.write(root / "umbrella" / ".gitignore", "*")
    gitRepo(root / "umbrella", "umbrella")
    gitRepo(nested, "nested")
    root
  }

  private def renderEmbed(ext: SnippetExtension): String = {
    val md = Markdown(List(ext.create()))
    md.renderToString(
      md.read("```scala\nEMBED:/umbrella/nested/code.scala\n```")
    )
  }

  test("permalink uses the outermost git repository by default") {
    val html = renderEmbed(SnippetExtension.create(nestedRepos()))

    expect(html.contains("https://github.com/owner/umbrella/blob/")) &&
    expect(html.contains("/nested/code.scala\""))
  }

  test("ignoredGitRoots makes the lookup continue to the nested repository") {
    val html = renderEmbed(
      SnippetExtension.create(
        nestedRepos(),
        ignoredGitRoots = Set(os.RelPath("umbrella"))
      )
    )

    expect(html.contains("https://github.com/owner/nested/blob/")) &&
    expect(!html.contains("owner/umbrella"))
  }
}
