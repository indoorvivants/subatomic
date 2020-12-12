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

case class MdocJSResult[C](
    original: C,
    markdownFile: os.Path,
    jsSnippetsFile: os.Path,
    jsInitialisationFile: os.Path
)

class MdocJSProcessor[C] private (mdoc: MdocJS, pwd: os.Path, toMdocFile: PartialFunction[C, MdocFile])
    extends Processor[C, MdocJSResult[C]] {

  val toMdocFileTotal = toMdocFile.lift

  override def register(content: C): Unit = {}

  override def retrieve(content: C): MdocJSResult[C] = {

    val mdocFile = toMdocFile(content)
    val result   = mdoc.process(pwd, mdocFile.path, mdocFile.dependencies)

    MdocJSResult(content, result.mdFile, result.mdjsFile, result.mdocFile)
  }
}

object MdocJSProcessor {
  def create[C](mdoc: MdocJS = new MdocJS, pwd: os.Path = os.pwd)(f: PartialFunction[C, MdocFile]) =
    new MdocJSProcessor[C](mdoc, pwd, f)
}
