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

package subatomic.builders

sealed trait SyntaxHighlighting extends Product with Serializable

object SyntaxHighlighting {
  case class HighlightJS(
      version: String = "11.7.0",
      languages: List[String] = List("scala"),
      theme: String = "tomorrow-night-blue"
  ) extends SyntaxHighlighting {
    private val urlBase =
      s"//cdn.jsdelivr.net/gh/highlightjs/cdn-release@$version/build"
    private def url(segments: String*): String = {
      (Seq(urlBase) ++ segments).mkString("/")
    }

    def styles: List[String] =
      List(
        url("styles", s"$theme.min.css")
      )

    def scripts: List[String] =
      List(
        url("highlight.min.js")
      ) ++ languages.map(lang => url("languages", s"$lang.min.js"))

    def initScript = """
    hljs.initHighlightingOnLoad();
  """.trim()
  }

  object HighlightJS {
    def default: HighlightJS = HighlightJS()

    def templateBlock(
        conf: HighlightJS
    ): Seq[scalatags.Text.TypedTag[String]] = {
      import scalatags.Text.all._

      val styles  = conf.styles.map(s => link(rel := "stylesheet", href := s))
      val scripts = conf.scripts.map(s => script(src := s))
      val initScript = script(raw(conf.initScript))

      (styles ++ scripts) ++ List(initScript)
    }
  }

  case class PrismJS(
      version: String = "1.26.0",
      theme: Option[String] = Some("okaidia"),
      additionalPlugins: List[String] = Nil,
      autoLoaderEnabled: Boolean = true
  ) extends SyntaxHighlighting {
    private val urlBase = s"//unpkg.com/prismjs@$version"
    private def url(segments: String*): String = {
      (Seq(urlBase) ++ segments).mkString("/")
    }

    def styles: List[String] =
      List(
        theme
          .map(name => url("themes", s"prism-$name.min.css"))
          .getOrElse(url("themes", "prism.min.css"))
      )

    private def components = List("core")

    def bodyScripts: List[String] =
      components.map(name => url("components", s"prism-$name.min.js")) ++
        (List("autoloader")
          .filter(_ => autoLoaderEnabled) ++
          additionalPlugins).distinct.map { name =>
          url("plugins", name, s"prism-$name.min.js")
        }
  }

  object PrismJS {
    def default: PrismJS = PrismJS()
    import scalatags.Text.all._

    case class Includes private[PrismJS] (
        conf: PrismJS
    ) {

      def styles = conf.styles.map(s => link(rel := "stylesheet", href := s))
      def bodyScripts = conf.bodyScripts.map(s => script(src := s))
    }

    def includes(conf: PrismJS): Includes = Includes(conf)
  }
}
