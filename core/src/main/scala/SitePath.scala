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

package com.indoorvivants.subatomic

import os.RelPath

case class SitePath(segments: Seq[String]) {
  override def toString() = segments.mkString("/", "/", "")

  def /(segment: String) = new SitePath(segments :+ segment)

  def /(rp: RelPath) = new SitePath(segments ++ rp.segments)

  def /(sp: SitePath) = new SitePath(segments ++ sp.segments)

  def up =
    if (segments.isEmpty)
      throw new IllegalStateException("Cannot go up on an empty path")
    else new SitePath(segments.take(segments.size - 1))

  def prepend(p: os.RelPath) = new SitePath(p.segments ++ segments)

  def prepend(p: SitePath) = new SitePath(p.segments ++ segments)
}

trait SiteRoot { self: SitePath => }
object SiteRoot extends SitePath(Seq.empty) with SiteRoot

object SitePath {
  implicit final class SitePathOps(private val sp: SitePath) extends AnyVal {
    def toRelPath = os.RelPath(sp.toString().drop(1))
  }

  def fromRelPath(rp: os.RelPath) = new SitePath(rp.segments)
}
