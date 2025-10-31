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

package subatomic.builders

import scala.collection.immutable

import subatomic.Markdown.Header

case class TOC(level: Vector[(Header, TOC)]) {
  val length: Int    = level.map(_._2.length + 1).sum
  def toStringPretty = {
    def go(levels: Vector[(Header, TOC)], indent: Int): String = {
      val id = " " * indent

      levels
        .map { case (h, toc) =>
          val nested = go(toc.level, indent + 1)
          s"$id - ${h.title}${if (nested.nonEmpty) "\n" + nested else ""}"
        }
        .mkString("\n")
    }
    go(this.level, 0)
  }
}
object TOC {
  val empty                               = TOC(Vector.empty)
  def build(headers: Vector[Header]): TOC = {

    def go(h: List[Header]): TOC = {
      h match {
        case head :: next =>
          val sub  = next.takeWhile(_.level > head.level)
          val rest = next.drop(sub.length)
          val sib  = go(rest)

          val nested = go(sub)

          TOC(Vector(head -> nested) ++ sib.level)

        case immutable.Nil => empty
      }
    }

    go(headers.toList)
  }
}
