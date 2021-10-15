package subatomic
package builders

import subatomic.builders.cli.BuildConfig
import subatomic.builders.cli.SearchConfig

object CliTest extends weaver.SimpleIOSuite {
  private def runBuild(args: String*) =
    cli.command.parse("build" +: args).flatMap {
      case bld: BuildConfig => Right(bld)
      case other            => Left(s"Expected build config, got $other instead")
    }

  private def runSearch(args: String*) =
    cli.command.parse("search" +: args).flatMap {
      case bld: SearchConfig => Right(bld)
      case other             => Left(s"Expected build config, got $other instead")
    }

  pureTest("CLI supports --disable-mdoc flag") {
    exists(runBuild("--disable-mdoc").toOption) { config =>
      expect(config.disableMdoc == true)
    }
  }

  pureTest("CLI supports --overwrite flag") {
    exists(runBuild("--force").toOption) { config =>
      expect(config.overwrite == true)
    }
  }

  pureTest("CLI supports --destination flag") {
    val dest = os.temp.dir()

    exists(runBuild(s"--destination", dest.toString()).toOption) { config =>
      expect(config.destination == dest)
    }
  }

  pureTest("CLI options are all optional") {
    exists(runBuild().toOption) { config =>
      expect.all(
        config.disableMdoc == false,
        config.overwrite == false
      )
    }
  }

  pureTest("CLI supports --test-search-cli flag") {
    exists(runSearch("--interactive").toOption) { config =>
      expect(config.mode == cli.Interactive)
    }
  }

  pureTest("CLI supports --test-search-query flag") {
    exists(runSearch("--query", "bla bla").toOption) { config =>
      expect(config.mode == cli.Query("bla bla"))
    }
  }
}
