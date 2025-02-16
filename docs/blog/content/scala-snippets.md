---
title: Scala snippets
date: 2024-12-17
tags: subatomic,blog,development,css
author: anton
---

```scala
case class Document(
    title: String,
    url: String,
    sections: Vector[Section]
)

case class Section(
    title: String,
    url: Option[String],
    content: String
) {
  override def toString() = {
    val cont = content.replace("\n", Console.BOLD + "\\n" + Console.RESET)

    s"Section($title, $url, $cont)"
  }
}
```
