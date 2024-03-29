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
package builders

trait Builder {
  val contentRoot: os.Path
  val cache: Cache
  val assetsRoot: Option[os.Path]      = None
  val base: SitePath                   = SiteRoot
  val highlighting: SyntaxHighlighting = SyntaxHighlighting.PrismJS.default
  val assetsFilter: os.Path => Boolean = _ => true
  val trackers: Seq[Tracker]           = Nil

  lazy val managedStyles: List[StylesheetPath] =
    assetsRoot
      .filter(assetsFilter)
      .map { path =>
        os.walk(path)
          .filter(_.ext == "css")
          .map(_.relativeTo(path))
          .map(rel => SiteRoot / "assets" / rel)
      }
      .getOrElse(Nil)
      .toList
      .map(StylesheetPath(_))

  lazy val managedScripts: List[ScriptPath] =
    assetsRoot
      .filter(assetsFilter)
      .map { path =>
        os.walk(path)
          .filter(_.ext == "js")
          .map(_.relativeTo(path))
          .map(rel => SiteRoot / "assets" / rel)
      }
      .getOrElse(Nil)
      .toList
      .map(ScriptPath(_))
}

object BuilderTemplate {
  import scalatags.Text.all._

  def managedStylesBlock(linker: Linker, styles: List[StylesheetPath]) = {
    styles.map { sp =>
      link(
        rel  := "stylesheet",
        href := linker.unsafe(_ => sp.path)
      )
    }
  }

  def managedScriptsBlock(linker: Linker, scripts: List[ScriptPath]) =
    scripts.map { sp =>
      script(src := linker.unsafe(_ => sp.path), defer)
    }
}

case class ScriptPath(path: SitePath)     extends AnyVal
case class StylesheetPath(path: SitePath) extends AnyVal
