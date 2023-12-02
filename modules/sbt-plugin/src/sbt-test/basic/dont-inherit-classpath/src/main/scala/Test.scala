package test

import scala.io.Source
import java.util.Properties

object Main {
  def main(args: Array[String]) = {
    val props = new Properties

    scala.util.Try {
      props.load(Source.fromResource("subatomic.properties").reader())
    }

    assert(
      !props.getProperty("classpath", "").contains("cats-effect"),
      "FAILED: cats-effect is added to the classpath in subatomic.properties, " +
        "but shouldn't have been"
    )
  }
}
