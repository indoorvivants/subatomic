package test

import scala.io.Source
import java.util.Properties

object Main {
  def main(args: Array[String]) = {
    val props = new Properties

    props.load(Source.fromResource("subatomic.properties").reader())

    assert(
      props.getProperty("classpath.default").contains("cats-effect"),
      "FAILED: cats-effect is not added to the classpath in subatomic.properties"
    )
  }
}
