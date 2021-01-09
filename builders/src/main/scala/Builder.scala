package subatomic
package builders

class Builder(
    val contentRoot: os.Path,
    val assetsRoot: Option[os.Path] = None,
    val base: SitePath = SiteRoot,
    val highlightJS: HighlightJS = HighlightJS.default
) {
  lazy val managedStyles: List[StylesheetPath] =
    assetsRoot
      .map { path =>
        os.walk(path).filter(_.ext == "css").map(_.relativeTo(path)).map(rel => SiteRoot / "assets" / rel)
      }
      .getOrElse(Nil)
      .toList
      .map(StylesheetPath)

  lazy val managedScripts: List[ScriptPath] =
    assetsRoot
      .map { path =>
        os.walk(path).filter(_.ext == "js").map(_.relativeTo(path)).map(rel => SiteRoot / "assets" / rel)
      }
      .getOrElse(Nil)
      .toList
      .map(ScriptPath)
}

object BuilderTemplate {
  import scalatags.Text.all._

  def managedStylesBlock(linker: Linker, styles: List[StylesheetPath]) = {
    styles.map { sp =>
      link(
        rel := "stylesheet",
        href := linker.unsafe(_ => sp.path)
      )
    }
  }

  def managedScriptsBlock(linker: Linker, scripts: List[ScriptPath]) =
    scripts.map { sp =>
      script(src := linker.unsafe(_ => sp.path))
    }

}

case class ScriptPath(path: SitePath)     extends AnyVal
case class StylesheetPath(path: SitePath) extends AnyVal
