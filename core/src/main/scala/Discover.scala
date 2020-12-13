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

import scala.jdk.CollectionConverters._

import com.vladsch.flexmark.ext.yaml.front.matter.AbstractYamlFrontMatterVisitor
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension

object Discover extends App {
  case class YamlAttributes(data: Map[String, List[String]]) {
    def requiredOne(field: String): String         = data(field).head
    def optionalOne(field: String): Option[String] = data.get(field).map(_.head)

    def requiredMany(field: String): List[String]         = data(field)
    def optionalMany(field: String): Option[List[String]] = data.get(field)

  }

  case class MarkdownDocument(
      path: os.Path,
      filename: String,
      attributes: YamlAttributes
  )

  def readYaml(path: os.Path, md: Markdown): YamlAttributes = {
    readYaml(os.read(path), md)
  }

  def readYaml(content: String, md: Markdown): YamlAttributes = {
    val doc = md.read(content)

    val visitor = new AbstractYamlFrontMatterVisitor()

    visitor.visit(doc)

    val data = visitor.getData().asScala.map { case (k, v) => k -> v.asScala.toList }.toMap

    YamlAttributes(data)
  }

  def someMarkdown[C](root: os.Path)(f: PartialFunction[MarkdownDocument, C]): Iterable[C] = {
    val md = Markdown(YamlFrontMatterExtension.create())

    val total = f.lift

    os.walk(root).filter(_.toIO.isFile()).filter(_.ext == "md").flatMap { path =>
      val filename   = path.baseName
      val attributes = readYaml(path, md)

      val doc = MarkdownDocument(
        path,
        filename,
        attributes
      )

      total(doc)
    }
  }
}
