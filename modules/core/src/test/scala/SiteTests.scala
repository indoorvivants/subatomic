package subatomic

import scala.util.Try

import weaver.Expectations
import weaver.{Log => WeaverLog}
import cats.effect.IO
import scala.collection.mutable.ListBuffer
import cats.effect.Blocker
import cats.effect.Resource

object SiteTests extends weaver.IOSuite {
  override type Res = Check
  override def sharedResource: Resource[IO, Res] = Blocker[IO].map(new Check(_))

  private val content = Vector(
    SiteRoot / "index.html"                     -> "indexpage!",
    SiteRoot / "pages" / "hello.html"           -> "hellopage",
    SiteRoot / "pages" / "hello" / "world.html" -> "worldpage!"
  )

  def read(p: os.Path): String = os.read(p)

  test("populate: addPage") { (res, log) =>
    val site = baseSite(log).populate { case (site, (path, content)) =>
      site.addPage(path, content)
    }

    res.check(site) { result =>
      expect.all(
        read(result / "pages" / "hello.html") == "hellopage",
        read(result / "pages" / "hello" / "world.html") == "worldpage!",
        read(result / "index.html") == "indexpage!"
      )
    }
  }

  test("doesn't overwrite the site by default") { (_, log) =>
    val site = baseSite(log).populate { case (site, (path, content)) =>
      site.addPage(path, content)
    }

    val destination = os.temp.dir()
    site.buildAt(destination) // should succeed

    IO(expect(Try(site.buildAt(destination)).isFailure)) // second time it fails
  }

  test("overwrites when explicitly told to") { (_, log) =>
    val site = baseSite(log).populate { case (site, (path, content)) =>
      site.addPage(path, content)
    }

    val destination = os.temp.dir()
    site.buildAt(destination) // should succeed

    IO(
      expect(Try(site.buildAt(destination, overwrite = true)).isSuccess)
    ) // second time it fails
  }

  test("copyAll - recursively copying assets") { (res, log) =>
    // create assets folder
    val tmpDir = os.temp.dir()
    os.makeDir.all(tmpDir / "assets" / "scripts")
    os.makeDir.all(tmpDir / "assets" / "styles")
    os.write(tmpDir / "assets" / "scripts" / "my.js", "My JS!")
    os.write(tmpDir / "assets" / "styles" / "my.css", "My CSS!")
    os.write(tmpDir / "assets" / "my.img", "My Image!")

    val site = baseSite(log).copyAll(tmpDir / "assets", SiteRoot / "my-assets")

    res.check(site) { result =>
      expect.all(
        read(result / "my-assets" / "my.img") == "My Image!",
        read(result / "my-assets" / "scripts" / "my.js") == "My JS!",
        read(result / "my-assets" / "styles" / "my.css") == "My CSS!"
      )
    }
  }

  test("addCopyOf - adding a copy") { (res, log) =>
    val tmpDir = os.temp.dir()
    os.write(tmpDir / "CNAME", "domain!")

    val site =
      baseSite(log).addCopyOf(SiteRoot / "test" / "CNAME", tmpDir / "CNAME")

    res.check(site) { result =>
      expect(
        read(result / "test" / "CNAME") == "domain!"
      )
    }
  }

  test("adrdProcessed - delays evaluation") { (res, log) =>
    var evaluations = 0

    val processor = Processor.simple[String, SiteAsset](stuff => {
      evaluations += 1; Page(stuff)
    })

    val site =
      baseSite(log).addProcessed(SiteRoot / "test", processor, "what's up")

    res.check(site) { result =>
      expect(evaluations == 1) and expect(read(result / "test") == "what's up")
    }
  }

  test("addProcessed - invokes register and then retrieve") { (res, log) =>
    val lifecycle = ListBuffer.empty[(String, String)]

    val processor = new Processor[String, SiteAsset] {
      override def register(content: String): Unit =
        lifecycle.append("register" -> content)

      override def retrieve(content: String): SiteAsset = {
        synchronized { lifecycle.append("retrieve" -> content): Unit }
        Page(s"processed: $content")
      }
    }

    val site = baseSite(log)
      .addProcessed(SiteRoot / "test", processor, "content-1")
      .addProcessed(SiteRoot / "test1", processor, "content-2")

    res.check(site) { result =>
      val checkLifecycle = expect(
        // registrations are synchronous
        lifecycle.take(2).toList == List(
          "register" -> "content-1",
          "register" -> "content-2"
        )
      ) and expect
        .all( // retrieval is run in parallel, we can't rely on the order
          lifecycle.drop(2).contains("retrieve" -> "content-1"),
          lifecycle.drop(2).contains("retrieve" -> "content-2")
        )

      val checkContent = expect.all(
        read(result / "test") == "processed: content-1",
        read(result / "test1") == "processed: content-2"
      )

      checkLifecycle && checkContent
    }
  }

  private def baseSite(log: WeaverLog[IO]) =
    Site
      .init(content)
      .changeLogger(s => log.info(s.replace("\n", "  ")).unsafeRunSync())

  class Check(blocker: Blocker) {
    def check[C](
        site: Site[C]
    )(f: os.Path => Expectations): IO[Expectations] = {
      blocker.blockOn(IO {
        val destination = os.temp.dir()

        site.buildAt(destination)

        f(destination)
      })
    }
  }

}
