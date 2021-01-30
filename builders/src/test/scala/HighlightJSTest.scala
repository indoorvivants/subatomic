package subatomic.builders

import weaver._

object HighlightJSTest extends SimpleIOSuite {
  pureTest("default values") {
    expect.all(
      HighlightJS.default.languages.contains("scala"),
      HighlightJS.default.theme == "default"
    )
  }
}
