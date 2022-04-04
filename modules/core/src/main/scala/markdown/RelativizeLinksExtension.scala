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

import java.{util => ju}

import scala.util.Failure
import scala.util.Try

import com.vladsch.flexmark.ast.Link
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html.HtmlRenderer.HtmlRendererExtension
import com.vladsch.flexmark.html.HtmlWriter
import com.vladsch.flexmark.html.LinkResolver
import com.vladsch.flexmark.html.LinkResolverFactory
import com.vladsch.flexmark.html.renderer.LinkResolverBasicContext
import com.vladsch.flexmark.html.renderer.NodeRenderer
import com.vladsch.flexmark.html.renderer.NodeRendererContext
import com.vladsch.flexmark.html.renderer.NodeRendererFactory
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler.CustomNodeRenderer
import com.vladsch.flexmark.html.renderer.ResolvedLink
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.data.DataHolder
import com.vladsch.flexmark.util.data.MutableDataHolder
// import java.nio.file.Paths
// import scala.jdk.CollectionConverters._
// import io.lemonlabs.uri.RelativeUrl
// import io.lemonlabs.uri.Url
// import io.lemonlabs.uri.config.UriConfig

object RelativizeLinksExtension {
  class Resolver(base: os.RelPath) extends LinkResolver {
    override def resolveLink(
        node: Node,
        context: LinkResolverBasicContext,
        link: ResolvedLink
    ): ResolvedLink = {
      def asUrl(u: String) = Try(new java.net.URL(u))
      val url              = link.getUrl()
      asUrl(url) match {
        case Failure(_) if url.startsWith("#") =>
          // hash
          link
        case Failure(_) =>
          val fakeBase = "http://localhost"
          val fakeUrl =
            if (url.startsWith("/")) fakeBase + url else fakeBase + "/" + url
          val parsed = new java.net.URI(fakeUrl)
          val pathSegments =
            if (parsed.getPath() == "/" || parsed.getPath() == "") Seq()
            else parsed.getPath().split("/").toSeq.tail

          val shiftedPath = "" +: (base.segments ++ pathSegments)
          val sb          = new StringBuilder
          sb.append(shiftedPath.mkString("/"))
          if (parsed.getQuery() != null) sb.append("?" + parsed.getQuery())
          if (parsed.getFragment() != null)
            sb.append("#" + parsed.getFragment())

          link.withUrl(sb.result())
        case _ => link
      }
    }

  }

  class ResolverFactory(base: os.RelPath) extends LinkResolverFactory {
    override def getAfterDependents(): ju.Set[Class[_]] = null

    override def getBeforeDependents(): ju.Set[Class[_]] = null

    override def affectsGlobalScope(): Boolean = false

    override def apply(x: LinkResolverBasicContext): LinkResolver =
      new Resolver(base)

  }

  class Renderer extends NodeRenderer {
    override def getNodeRenderingHandlers(): ju.Set[NodeRenderingHandler[_]] = {
      val s = new ju.HashSet[NodeRenderingHandler[_]]

      val rend = new CustomNodeRenderer[Link] {
        override def render(
            link: Link,
            x: NodeRendererContext,
            writer: HtmlWriter
        ): Unit = {
          x.delegateRender()
        }
      }

      s.add(new NodeRenderingHandler(classOf[Link], rend))

      s
    }

  }

  class RendererFactory extends NodeRendererFactory {
    override def apply(options: DataHolder): NodeRenderer = new Renderer
  }

  class Extension(base: os.RelPath) extends HtmlRendererExtension {
    override def rendererOptions(x: MutableDataHolder): Unit = {}

    override def extend(
        builder: HtmlRenderer.Builder,
        rendererType: String
    ): Unit = {
      builder.linkResolverFactory(new ResolverFactory(base))
      val _ = builder.nodeRendererFactory(new RendererFactory)
    }

  }

  def apply(base: os.RelPath): Extension = {
    new Extension(base)
  }

  def apply(sp: SitePath): Extension = apply(sp.toRelPath)
}
