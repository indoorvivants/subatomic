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
package search
package cli

import scala.util.Try
import scala.collection.compat._

object SearchCLI {
  def main(argArr: Array[String]): Unit = {
    val args = argArr.toList

    val _ = args match {
      case "index" :: folder :: indexfile :: _ => buildIndex(folder, indexfile)
      case "search" :: indexFile :: query :: _ => runSearch(indexFile, query)
      case _                                   => throw new RuntimeException("command must be index or search")
    }

  }

  def buildIndex(folder: String, indexfile: String) = {
    for {
      root   <- path(folder)
      target <- path(indexfile)
      idx = indexPath(root)
      _   = os.write(target, idx.asJsonString)
      _   = println("howdy")
    } yield println("indexing complete")
  }

  private def indexPath(path: os.Path): SearchIndex = {
    val iter = os.walk(path).iterator.filter(_.toIO.isFile()).map(p => p -> os.read(p)).to(Iterable)

    val indexer = Indexer.default[(os.Path, String)](iter)

    indexer.processAll { case (path, content) =>
      Document.section(s"Doc at $path", path.toString(), content)
    }
  }

  def path(partial: String): Try[os.Path] = {

    val rp   = Try(os.RelPath(partial))
    val root = Try(os.Path(partial)).orElse(rp.map(os.pwd / _))

    root
  }

  def runSearch(indexFile: String, query: String) = {
    for {
      indexPath <- path(indexFile)
      loaded = os.read(indexPath)
      idx    = upickle.default.read[SearchIndex](loaded)
      search = Search.query(idx, query, debug = false)
    } yield ()
  }
}
