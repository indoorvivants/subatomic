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
package builders.blog

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import scala.util.Try

import com.monovore.decline.Opts

/** Blog-specific CLI commands */
private[blog] object BlogCommands:
  enum Extra:
    case NewPost

  val extra: Opts[Extra] =
    Opts.subcommand("post", "Interactively create a new blog post")(
      Opts(Extra.NewPost)
    )

  def run(extra: Extra, blog: Blog): Unit =
    extra match
      case Extra.NewPost => addPost(blog.contentRoot)

  private def addPost(base: os.Path): Unit =
    import cue4s.*

    Prompts.sync.use: prompts =>
      val today =
        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
      val date = prompts
        .text(
          "Date",
          _.default(today).validate(str =>
            Try(LocalDate.parse(str)).failed.toOption
              .map("Invalid date: " + _.getMessage())
              .map(PromptError.apply)
          )
        )
        .getOrThrow
      val title        = prompts.text("Title").getOrThrow
      val description  = prompts.text("Description").getOrThrow
      val tags         = prompts.text("Tags (comma separated)").getOrThrow
      val computedSlug = title.toLowerCase
        .filter(c => c.isLetterOrDigit || c.isWhitespace)
        .replaceAll(raw"(\s+)", "-")
      val slug = prompts
        .text(
          "Slug",
          _.default(computedSlug)
            .validate(s =>
              Option.when(os.exists(base / s"$s.md"))(
                PromptError(s"$s.md already exists")
              )
            )
        )
        .getOrThrow

      val frontmatter = s"""
        |---
        |title: $title
        |date: $date
        |tags: $tags
        |description: $description
        |---
        """.trim.stripMargin

      val target = base / s"$slug.md"

      if os.exists(target) then SubatomicError.raise(s"$target already exists!")
      else
        os.write.over(target, frontmatter)
        println(s"Written $target")
      end if
  end addPost
end BlogCommands
