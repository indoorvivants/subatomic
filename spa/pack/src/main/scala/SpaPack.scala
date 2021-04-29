package subatomic
package spa


object SpaPack {
  def fullJS: String = _fullJS

  private val filename = "spa.js"

  private lazy val _fullJS = {
    val classloader = this.getClass.getClassLoader
    Option(classloader.getResourceAsStream(filename)) match {
      case Some(stream) =>
        io.Source.fromInputStream(stream).getLines().mkString("\n")
      case None =>
        throw new RuntimeException(
          s"Trying to load a packaged $filename failed"
        )
    }
  }
}
