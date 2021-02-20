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

class MdocJSProcessor[C] private (pwd: os.Path, toMdocFile: PartialFunction[C, MdocFile])
    extends Processor[C, MdocJSResult[C]] {

  private type Key = MdocConfiguration

  private val internalFiles    = scala.collection.mutable.Map.empty[Key, Map[C, MdocFile]]
  private val internalTriggers = scala.collection.mutable.Map.empty[C, Key]
  private val internalResults  = scala.collection.mutable.Map.empty[Key, Map[C, MdocJSResult[C]]]
  private val internalMdocs    = scala.collection.mutable.Map.empty[Key, MdocJS]

  val toMdocFileTotal = toMdocFile.lift

  def extractKey(f: MdocFile): Key = f.config

  override def register(content: C): Unit = {
    toMdocFileTotal(content).foreach { mdocFile =>
      val key = extractKey(mdocFile)

      internalTriggers.update(content, key)
      internalFiles.update(key, internalFiles.getOrElse(key, Map.empty).updated(content, mdocFile))
      internalMdocs.update(key, new MdocJS(key))
    }
  }

  override def retrieve(content: C): MdocJSResult[C] = {

    val triggerKey = internalTriggers(content)
    println(triggerKey)

    if (!internalResults.contains(triggerKey)) {
      val filesToProcess = internalFiles(triggerKey)
      val mdoc           = internalMdocs(triggerKey)

      val result = mdoc.processAll(pwd, filesToProcess.map(_._2).map(_.path).toSeq).toMap

      val results = filesToProcess.map {
        case (content, mdocFile) =>
          val mdjsResult = result(mdocFile.path)

          content -> MdocJSResult(content, mdjsResult.mdFile, mdjsResult.mdjsFile, mdjsResult.mdocFile)

      }

      internalResults.update(triggerKey, results)
    }

    internalResults(triggerKey)(content)
  }
}

object MdocJSProcessor {
  def create[C](pwd: os.Path = os.pwd)(f: PartialFunction[C, MdocFile]) =
    new MdocJSProcessor[C](pwd, f)
}
