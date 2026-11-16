/*
 * Copyright 2020 Anton Sviridov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package subatomic

import java.net.URL
import java.net.URLClassLoader

/** Parent classloader for the isolated mdoc classloader.
  *
  * Only JDK classes (via the platform classloader) and the classes that form
  * the API boundary between Subatomic and mdoc (`mdoc.interfaces` and the
  * coursier interface it references) are visible to mdoc from the host.
  * Everything else – Scala library, compiler, mdoc itself – is loaded from the
  * mdoc classpath, which allows running mdoc for a Scala version different from
  * the one Subatomic was compiled with.
  */
private[subatomic] class MdocSharedClassLoader(host: ClassLoader)
    extends ClassLoader(ClassLoader.getPlatformClassLoader()) {
  override def findClass(name: String): Class[?] = {
    if (MdocClassLoader.isShared(name)) host.loadClass(name)
    else throw new ClassNotFoundException(name)
  }
}

private[subatomic] object MdocClassLoader {
  private val sharedPrefixes = List("mdoc.interfaces.", "coursierapi.")

  def isShared(name: String): Boolean = sharedPrefixes.exists(name.startsWith)

  def create(classpath: Seq[URL]): URLClassLoader =
    new URLClassLoader(
      classpath.toArray,
      new MdocSharedClassLoader(classOf[mdoc.interfaces.Mdoc].getClassLoader())
    )
}
