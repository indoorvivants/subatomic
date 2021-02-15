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

  private type Key = Set[String]

  private val internalFiles    = scala.collection.mutable.Map.empty[Key, Map[C, MdocFile]]
  private val internalTriggers = scala.collection.mutable.Map.empty[C, Key]
  private val internalResults  = scala.collection.mutable.Map.empty[Key, Map[C, MdocJSResult[C]]]

  val toMdocFileTotal = toMdocFile.lift

  def extractKey(f: MdocFile): Key = f.dependencies

  override def register(content: C): Unit = {
    println(content); println(toMdocFileTotal(content))
    toMdocFileTotal(content).foreach { mdocFile =>
      val key = extractKey(mdocFile)

      internalTriggers.update(content, key)
      internalFiles.update(key, internalFiles.getOrElse(key, Map.empty).updated(content, mdocFile))
    }
  }

  override def retrieve(content: C): MdocJSResult[C] = {

    val triggerKey = internalTriggers(content)

    if (!internalResults.contains(triggerKey)) {
      val filesToProcess = internalFiles(triggerKey)

      val result = mdoc.processAll(pwd, filesToProcess.map(_._2).map(_.path).toSeq, triggerKey).toMap

      val results = filesToProcess.map {
        case (content, mdocFile) =>
          val mdjsResult = result(mdocFile.path)

          content -> MdocJSResult(content, mdjsResult.mdFile, mdjsResult.mdjsFile, mdjsResult.mdocFile)

      }

      internalResults.update(triggerKey, results)
    }

    internalResults(triggerKey)(content)

    // val mdocFile = toMdocFile(content)
    // val result   = mdoc.processAll(pwd, Seq(mdocFile.path), mdocFile.dependencies).head._2

    // MdocJSResult(content, result.mdFile, result.mdjsFile, result.mdocFile)
  }
}

object MdocJSProcessor {
  def create[C](mdoc: MdocJS = new MdocJS, pwd: os.Path = os.pwd)(f: PartialFunction[C, MdocFile]) =
    new MdocJSProcessor[C](mdoc, pwd, f)
}
