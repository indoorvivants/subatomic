package subatomic.builders

trait MarkdownTheme {
  import WithClassname.none

  object UnorderedList {
    var Container = none
    var Item      = none
  }
  object OrderedList {
    var Container = none
    var Item      = none
  }
  var Link         = none
  var Paragraph    = none
  var Quote        = none
  var Preformatted = none
  var Code         = none
  var InlineCode   = none

  object Headings {
    var H1 = none
    var H2 = none
    var H3 = none
    var H4 = none
    var H5 = none
  }
}

object MarkdownTheme {

  object none    extends MarkdownTheme
  object default extends Default

  trait Default extends MarkdownTheme {
    import WithClassname.{apply => c}

    val headingTheme =
      s"underline hover:no-underline"
    Paragraph = c("leading-relaxed text-lg my-4 break-words w-full")
    Link = c("underline hover:no-underline text-sky-700")
    Preformatted = c("whitespace-pre-wrap")
    Code = c("whitespace-pre-wrap")
    UnorderedList.Container = c("list-disc ml-8 block text-lg my-2")
    UnorderedList.Item = c("break-words")
    OrderedList.Container = c("list-decimal ml-8 block text-lg my-2")
    OrderedList.Item = c("break-words")
    Headings.H1 = c(s"text-2xl font-bold my-2 $headingTheme")
    Headings.H2 = c(s"text-xl font-bold my-2 $headingTheme")
    Headings.H3 = c(s"text-lg font-bold my-2 $headingTheme")
    Quote = c("p-4 text-slate-700 border-l-4")
    InlineCode = c(
      "px-1 bg-slate-600 text-white rounded break-words"
    )

  }

}

