package com.indoorvivants.subatomic

import os.RelPath

case class SitePath(segments: Seq[String]) {
  override def toString() = segments.mkString("/", "/", "")

  def /(segment: String) = new SitePath(segments :+ segment)

  def /(rp: RelPath) = new SitePath(segments ++ rp.segments)

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
