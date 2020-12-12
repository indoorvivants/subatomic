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

import scala.collection.mutable.ListBuffer

case class MdocResult[C](original: C, resultFile: os.Path)

class MdocProcessor[C] private (mdoc: Mdoc, pwd: os.Path, toMdocFile: PartialFunction[C, MdocFile])
    extends Processor[C, MdocResult[C]] {
  private var preparedMdoc: Option[mdoc.PreparedMdoc[C]]   = None
  private val registeredContent: ListBuffer[(C, MdocFile)] = ListBuffer.empty

  val toMdocFileTotal = toMdocFile.lift

  override def register(content: C): Unit = {
    toMdocFileTotal(content).foreach { file =>
      registeredContent.append(content -> file)
    }
  }

  override def retrieve(content: C): MdocResult[C] = {
    if (preparedMdoc.isEmpty)
      preparedMdoc = Some(mdoc.prepare(registeredContent, Some(pwd)))

    MdocResult(content, preparedMdoc.get.get(content))
  }
}

object MdocProcessor {
  def create[C](mdoc: Mdoc = new Mdoc, pwd: os.Path = os.pwd)(f: PartialFunction[C, MdocFile]) =
    new MdocProcessor[C](mdoc, pwd, f)
}
