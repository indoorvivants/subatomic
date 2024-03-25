package subatomic

import java.net.URL
import java.net.URLClassLoader

// final class FilteringClassLoader(parent: ClassLoader) extends ClassLoader(parent) {
//   private val parentPrefixes = Array(
//     "java.",
//     "scala.",
//     // "org.scalajs.linker.",
//     // "org.scalajs.logging.",
//     "sun.reflect.",
//     "jdk.internal.reflect.",
//     // "mdoc."
//   )

//   override def loadClass(name: String, resolve: Boolean): Class[_] = {
//     if (parentPrefixes.exists(name.startsWith _))
//       super.loadClass(name, resolve)
//     else
//       null
//   }
// }

class MdocClassLoader(parent: ClassLoader)
    extends ClassLoader(ClassLoader.getSystemClassLoader.getParent) {
  override def findClass(name: String): Class[_] = {
    val isShared =
      name.startsWith("mdoc.interfaces") || name.startsWith("coursierapi")
    if (isShared) {
      parent.loadClass(name)
    } else {
      throw new ClassNotFoundException(name)
    }
  }
}

object MdocClassLoader {
  def create(classpath: Array[URL]): ClassLoader =
    new URLClassLoader(
      classpath,
      new MdocClassLoader(getClass.getClassLoader())
    )

}
