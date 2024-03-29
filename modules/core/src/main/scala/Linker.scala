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

import io.lemonlabs.uri.Uri
import io.lemonlabs.uri.Url
import io.lemonlabs.uri.UrlPath

class Linker(content: Vector[(SitePath, _)], base: SitePath) {
  private val mp = content.map(_._1).toSet;

  def unsafe(f: (SiteRoot with SitePath => SitePath)): String = {
    f(SiteRoot).prepend(base).toString
  }

  def resolve(f: (SiteRoot with SitePath => SitePath)): String = {
    val rawLocation = f(SiteRoot)
    if (mp(rawLocation)) rawLocation.prepend(base).toString
    else
      throw new IllegalArgumentException(
        s"Could not resolve $rawLocation location in the site"
      )

  }

  def resolvePath(f: (SiteRoot with SitePath => SitePath)): SitePath = {
    val rawLocation = f(SiteRoot)
    if (mp(rawLocation)) rawLocation.prepend(base)
    else
      throw new IllegalArgumentException(
        s"Could not resolve $rawLocation location in the site"
      )

  }

  def absoluteUrl(u: Url, f: (SiteRoot with SitePath => SitePath)): Url = {
    val path = f(SiteRoot).prepend(base)
    u.withPath(UrlPath(path.segments))
  }

  def absoluteUrlForContent(u: Url, piece: Any): Url = {
    val path = findPath(piece)

    u.withPath(UrlPath(path.segments))
  }

  def findPath(piece: Any): SitePath = {
    content
      .find(_._2 == piece)
      .map(found => resolvePath(_ / found._1))
      .getOrElse(
        throw new IllegalArgumentException(
          s"Could not resolve $piece content in the site"
        )
      )
  }

  def find(piece: Any): String = {
    content
      .find(_._2 == piece)
      .map(found => resolve(_ / found._1))
      .getOrElse(
        throw new IllegalArgumentException(
          s"Could not resolve $piece content in the site"
        )
      )
  }

  def findRelativePath(piece: Any): SitePath = {
    content
      .find(_._2 == piece)
      .map(found => found._1)
      .getOrElse(
        throw new IllegalArgumentException(
          s"Could not resolve $piece content in the site"
        )
      )
  }

  def findAbsolutePath(piece: Any): SitePath = {
    content
      .find(_._2 == piece)
      .map(found => found._1.prepend(base))
      .getOrElse(
        throw new IllegalArgumentException(
          s"Could not resolve $piece content in the site"
        )
      )
  }

  def root = base.toString()
}
