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

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes
import org.scalajs.dom

abstract class LaminarApp(elementId: String) {
  def root = dom.document.getElementById(elementId)

  def app: nodes.ReactiveElement.Base

  def main(args: Array[String]): Unit = {
    discard[Subscription] {
      documentEvents.onDomContentLoaded.foreach { _ =>
        discard[RootNode](render(root, app))
      }(unsafeWindowOwner)
    }
  }
}
