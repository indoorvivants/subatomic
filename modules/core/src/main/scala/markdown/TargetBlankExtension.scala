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

import com.vladsch.flexmark.ast.Link
import com.vladsch.flexmark.html.AttributeProvider
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html.HtmlRenderer.HtmlRendererExtension
import com.vladsch.flexmark.html.IndependentAttributeProviderFactory
import com.vladsch.flexmark.html.renderer.AttributablePart
import com.vladsch.flexmark.html.renderer.LinkResolverContext
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.data.MutableDataHolder
import com.vladsch.flexmark.util.html.MutableAttributes
import com.vladsch.flexmark.util.sequence.BasedSequence

object TargetBlankExtension {

  class Extension extends HtmlRendererExtension {
    override def rendererOptions(x: MutableDataHolder): Unit = {}

    override def extend(
        builder: HtmlRenderer.Builder,
        rendererType: String
    ): Unit = {
      val _ = builder.attributeProviderFactory(Provider.Factory())
    }

  }

  class Provider extends AttributeProvider {
    override def setAttributes(
        node: Node,
        part: AttributablePart,
        attributes: MutableAttributes
    ): Unit =
      if (node.isInstanceOf[Link] && part == AttributablePart.LINK) {
        val link = node.asInstanceOf[Link]

        if (link.getAnchorRef() == BasedSequence.NULL) {
          val _ = attributes.replaceValue("target", "_blank")
        }
      }
  }

  object Provider {
    def Factory() = new IndependentAttributeProviderFactory {
      override def apply(context: LinkResolverContext): AttributeProvider =
        new Provider
    }
  }

  def create() = new Extension

}
