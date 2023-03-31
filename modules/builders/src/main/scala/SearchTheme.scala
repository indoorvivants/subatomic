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
    ResultsContainer = c("absolute w-full lg:w-[500px] border-2 border-slate-400 top-full right-auto left-0 rounded-md bg-white z-50 p-4")
    DocumentUrl = c("font-bold text-sky-700 text-xl underline hover:no-underline")
    DocumentTitle = c("font-bold text-xl text-black")
    SectionsContainer = c("m-2")
    SectionUrl = c("text-lg underline hover:no-underline text-black")
  }

}
