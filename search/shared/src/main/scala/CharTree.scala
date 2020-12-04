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

import scala.annotation.tailrec
import scala.collection.immutable.Nil
import scala.collection.{mutable => mut}

case class CharTree(
    data: Map[Char, CharTree],
    terminal: Option[TermIdx]
) {
  def find(chars: List[Char]): Option[TermIdx] = {
    @tailrec
    def go(chars: List[Char], node: CharTree): Option[TermIdx] = {
      (chars, node) match {
        case (h :: t, CharTree(children, _)) if children.contains(h) =>
          go(t, children(h))
        case (Nil, CharTree(_, Some(idx))) => Some(idx)
        case _                             => None
      }
    }

    go(chars, this)
  }

  def find(termName: TermName): Option[TermIdx] = find(termName.value.toList)
}

object CharTree {

  private case class MutableCharTree private (
      children: mut.Map[Char, MutableCharTree],
      var terminal: Option[TermIdx]
  ) {
    def immutable: CharTree = {
      def go(node: MutableCharTree): CharTree = {
        node match {
          case MutableCharTree(ch, _) if ch.isEmpty =>
            CharTree(Map.empty, node.terminal)
          case MutableCharTree(children, term) =>
            CharTree(children.map { case (ch, nd) => ch -> go(nd) }.toMap, term)
        }
      }

      go(this)
    }
  }

  def build(terms: Iterable[(TermName, TermIdx)]) = {
    val node = MutableCharTree(mut.Map.empty, None)

    terms.foreach {
      case (name, idx) =>
        add(node, name.value.toList, idx)
    }

    node.immutable
  }

  private def add(
      node: MutableCharTree,
      chars: List[Char],
      idx: TermIdx
  ): Unit =
    node match {
      case MutableCharTree(children, _) =>
        chars match {
          case head :: tail if tail.nonEmpty =>
            val matchingBranch = children.find(_._1 == head)

            matchingBranch match {
              case Some(value) =>
                val next = value._2

                add(next, tail, idx)
              case None =>
                val node =
                  MutableCharTree(mut.Map.empty[Char, MutableCharTree], None)
                children.update(
                  head,
                  node
                )

                add(node, tail, idx)
            }

          case head :: Nil =>
            val matchingBranch = children.find(_._1 == head)

            matchingBranch match {
              case Some(value) => value._2.terminal = Some(idx)
              case None =>
                val newMutableCharTree =
                  MutableCharTree(mut.Map.empty, Some(idx))

                children.update(head, newMutableCharTree)

            }
          case _ => ()
        }

    }

  def prettyPrint(ct: CharTree): Unit = {

    def go(node: CharTree, level: Int): Unit = {
      def _print(s: String) = println((" " * level) + s)
      node match {
        case CharTree(children, termIdx) =>
          _print(termIdx.toString)
          children.foreach {
            case (char, tree) =>
              _print(s"--'$char'-->")
              go(tree, level + 1)
          }
        case _ => ()
      }
    }

    go(ct, 0)
  }
}

object Test {
  def main(args: Array[String]): Unit = {
    val words = List("taps", "tops", "top", "tap").zipWithIndex.map {
      case (n, idx) => TermName(n) -> TermIdx(idx)
    }

    val struct = CharTree.build(words)

    println(struct.find("top".toList))

    CharTree.prettyPrint(struct)
  }
}
