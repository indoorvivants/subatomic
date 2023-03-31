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

sealed trait ExtraStyles extends Product with Serializable
object ExtraStyles {
  case class TailwindApply(classes: String) extends ExtraStyles
  case class CSS(value: String)             extends ExtraStyles
}

trait WithClassname {
  def className: Option[String]
}
object WithClassname {
  private class Impl(value: Option[String]) extends WithClassname {
    def className: Option[String] = value
  }
  def apply(value: String): WithClassname           = new Impl(Some(value))
  def none: WithClassname                           = new Impl(None)
  def define(name: String, es: ExtraStyles): Define = new Define(name, es)
  

}

case class Define(name: String, styles: ExtraStyles) extends WithClassname {
  override def className: Option[String] = Some(name)
}

case class Stylesheet(definitions: List[Define]) extends WithClassname {
  override def className: Option[String] = None
}
