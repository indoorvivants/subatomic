package subatomic


import weaver.SimpleMutableIOSuite
import subatomic.Discover.MarkdownDocument
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension
import weaver.Expectations
import subatomic.Discover.YamlAttributes

object DiscoverTests extends SimpleMutableIOSuite {

  pureTest("readYaml - reading YAML-like attributes from a markdown file") {
    val content =
      """
    |---
    | test: 1
    | hello: world
    |---
    |
    |what's up
    """.stripMargin

    check(content) { attributes =>
      expect.all(
        attributes.requiredOne("test") == "1",
        attributes.requiredOne("hello") == "world",
        attributes.optionalOne("bla") == None,
        attributes.optionalOne("test") == Some("1")
      )
    }
  }

  pureTest("someMarkdown - discovering content in a folder") {
    val tmpDir = os.temp.dir()

    def contents(attributes: (String, String)*) = {
      (("---" :: attributes.toList.map { case (k, v) => k + ":" + v }) ++ List("---", "hello!")).mkString("\n")
    }

    os.write.over(tmpDir / "1.md", contents("test" -> "1", "hello" -> "bla"))
    os.write.over(tmpDir / "2.md", contents("test" -> "2", "hello" -> "bla-bla"))
    os.write.over(tmpDir / "3.md", contents("test" -> "1", "hello" -> "bla-bla", "opt" -> "yay"))

    case class MyContent(
        test: String,
        hello: String,
        opt: Option[String],
        path: os.Path
    )

    val results = Discover
      .someMarkdown(tmpDir) {
        case MarkdownDocument(path, filename, attributes) =>
          SiteRoot / s"$filename.html" -> MyContent(
            attributes.requiredOne("test"),
            attributes.requiredOne("hello"),
            attributes.optionalOne("opt"),
            path
          )
      }
      .toSet

    expect(
      results == Set(
        SiteRoot / "1.html" -> MyContent("1", "bla", None, tmpDir / "1.md"),
        SiteRoot / "2.html" -> MyContent("2", "bla-bla", None, tmpDir / "2.md"),
        SiteRoot / "3.html" -> MyContent("1", "bla-bla", Some("yay"), tmpDir / "3.md")
      )
    )
  }

  def check(content: String)(f: YamlAttributes => Expectations) = {
    val md = Markdown(YamlFrontMatterExtension.create())
    f(Discover.readYaml(content, md, os.pwd / "test-path.md"))
  }
}
