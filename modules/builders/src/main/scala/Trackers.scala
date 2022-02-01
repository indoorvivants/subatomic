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

import scalatags.Text

sealed trait Tracker extends Product with Serializable
object Tracker {
  case class GoogleAnalytics(
      measurementId: String,
      config: GoogleAnalytics.Config = GoogleAnalytics.Config(),
      additional: Map[String, GoogleAnalytics.Config] = Map.empty
  ) extends Tracker {
    def scripts: Seq[Text.TypedTag[String]] = {
      import scalatags.Text.all._
      val ref = script(src := s"https://www.googletagmanager.com/gtag/js?id=$measurementId")
      val init = script(
        raw(
          s""" window.dataLayer = window.dataLayer || [];
              function gtag(){window.dataLayer.push(arguments);}
              gtag('js', new Date());

              gtag('config', '$measurementId');"""
        )
      )

      Seq(ref, init)
    }
  }

  object GoogleAnalytics {
    case class Config(send_page_view: Boolean = true)
  }
}
