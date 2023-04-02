package subatomic.builders

import weaver._
import SyntaxHighlighting._

object SyntaxHighlightingTest extends SimpleIOSuite {
  pureTest("default values") {
    expect.all(
      HighlightJS.default.languages.contains("scala"),
      HighlightJS.default.theme == "tomorrow-night-blue",
      PrismJS.default.autoLoaderEnabled == true,
      PrismJS.default.additionalPlugins.isEmpty,
      PrismJS.default.theme.contains("okaidia")
    )
  }
}
