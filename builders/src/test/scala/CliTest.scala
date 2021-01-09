package subatomic
package builders

object CliTest extends weaver.SimpleIOSuite {
  private def run(args: String*) = cli.command.parse(args)

  pureTest("CLI supports --disable-mdoc flag") {
    exists(run("--disable-mdoc")) { config =>
      expect(config.disableMdoc == true)
    }
  }

  pureTest("CLI supports --overwrite flag") {
    exists(run("--overwrite")) { config =>
      expect(config.overwrite == true)
    }
  }

  pureTest("CLI supports --destination flag") {
    val dest = os.temp.dir()

    exists(run(s"--destination", dest.toString())) { config =>
      expect(config.destination == dest)
    }
  }

  pureTest("CLI options are all optional") {
    exists(run()) { config =>
      expect.all(
        config.disableMdoc == false,
        config.overwrite == false
      )
    }
  }
}
