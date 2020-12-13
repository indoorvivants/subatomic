package subatomic

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import weaver.SimpleMutableIOSuite
import weaver.Expectations
import weaver.{Log => WeaverLog}
import cats.effect.IO
import scala.collection.mutable.ListBuffer

object SiteTests extends SimpleMutableIOSuite {

  private val content = Vector(
    SiteRoot / "index.html"                     -> "indexpage!",
    SiteRoot / "pages" / "hello.html"           -> "hellopage",
    SiteRoot / "pages" / "hello" / "world.html" -> "worldpage!"
  )

  loggedTest("populate: addPage") { log =>
    val site = baseSite(log).populate {
      case (site, (path, content)) =>
        site.addPage(path, content)
    }

    check(site) { result =>
      expect.all(
        os.read(result / "pages" / "hello.html") == "hellopage",
        os.read(result / "pages" / "hello" / "world.html") == "worldpage!",
        os.read(result / "index.html") == "indexpage!"
      )
    }
  }

  loggedTest("copyAll - recursively copying assets") { log =>
    // create assets folder
    val tmpDir = os.temp.dir()
    os.makeDir.all(tmpDir / "assets" / "scripts")
    os.makeDir.all(tmpDir / "assets" / "styles")
    os.write(tmpDir / "assets" / "scripts" / "my.js", "My JS!")
    os.write(tmpDir / "assets" / "styles" / "my.css", "My CSS!")
    os.write(tmpDir / "assets" / "my.img", "My Image!")

    val site = baseSite(log).copyAll(tmpDir / "assets", SiteRoot / "my-assets")

    check(site) { result =>
      expect.all(
        os.read(result / "my-assets" / "my.img") == "My Image!",
        os.read(result / "my-assets" / "scripts" / "my.js") == "My JS!",
        os.read(result / "my-assets" / "styles" / "my.css") == "My CSS!"
      )
    }
  }

  loggedTest("addCopyOf - adding a copy") { log =>
    val tmpDir = os.temp.dir()
    os.write(tmpDir / "CNAME", "domain!")

    val site = baseSite(log).addCopyOf(SiteRoot / "test" / "CNAME", tmpDir / "CNAME")

    check(site) { result =>
      expect(
        os.read(result / "test" / "CNAME") == "domain!"
      )
    }
  }

  loggedTest("addProcessed - delays evaluation") { log =>
    var evaluations = 0

    val processor = Processor.simple[String, SiteAsset](stuff => { evaluations += 1; Page(stuff) })

    val site = baseSite(log).addProcessed(SiteRoot / "test", processor, "what's up")

    check(site) { result =>
      expect(evaluations == 1) and expect(os.read(result / "test") == "what's up")
    }
  }

  loggedTest("addProcessed - invokes register and then retrieve") { log =>
    val lifecycle = ListBuffer.empty[(String, String)]

    val processor = new Processor[String, SiteAsset] {
      override def register(content: String): Unit = lifecycle.append("register" -> content)

      override def retrieve(content: String): SiteAsset = {
        synchronized { lifecycle.append("retrieve" -> content) }
        Page(s"processed: $content")
      }
    }

    val site = baseSite(log)
      .addProcessed(SiteRoot / "test", processor, "content-1")
      .addProcessed(SiteRoot / "test1", processor, "content-2")

    check(site) { result =>
      val checkLifecycle = expect(
        // registrations are synchronous
        lifecycle.take(2).toList == List(
          "register" -> "content-1",
          "register" -> "content-2"
        )
      ) and expect.all( // retrieval is run in parallel, we can't rely on the order
        lifecycle.drop(2).contains("retrieve" -> "content-1"),
        lifecycle.drop(2).contains("retrieve" -> "content-2")
      )

      val checkContent = expect.all(
        os.read(result / "test") == "processed: content-1",
        os.read(result / "test1") == "processed: content-2"
      )

      checkLifecycle && checkContent
    }
  }

  private def baseSite(log: WeaverLog[IO]) =
    Site.init(content).changeLogger(s => log.info(s.replace("\n", "  ")).unsafeRunSync())

  private def check[C](site: Site[C])(f: os.Path => Expectations): IO[Expectations] = {
    IO {
      val destination = os.temp.dir()

      site.buildAt(destination)

      f(destination)
    }
  }

}
