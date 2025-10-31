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

trait SearchTheme {

  import WithClassname.none

  var Container         = none
  var ResultsContainer  = none
  var Result            = none
  var DocumentUrl       = none
  var DocumentTitle     = none
  var SectionsContainer = none
  var SectionUrl        = none
  var Input             = none

}

object SearchTheme {
  import WithClassname.{apply => c}
  object none    extends SearchTheme
  object default extends Default
  trait Default  extends SearchTheme {
    Container = c("relative")
    Input = c("text-xl rounded-md p-2 w-full text-black")
    ResultsContainer = c(
      "absolute w-full lg:w-[500px] border-2 border-slate-400 top-full right-auto left-0 rounded-md bg-white z-50 p-4"
    )
    DocumentUrl = c(
      "font-bold text-sky-700 text-xl underline hover:no-underline"
    )
    DocumentTitle = c("font-bold text-xl text-black")
    SectionsContainer = c("m-2")
    SectionUrl = c("text-lg underline hover:no-underline text-black")
  }

}
