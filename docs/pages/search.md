---
id: search
title: Search 
---

# Search

Subatomic comes with a very simple built-in search.

To enable it on any of the builders, just add

```scala
LibrarySite(
 ...
 search = true
 ...
)
```

## How does it work?

1. Your documents get indexed (text per section)
2. The search index is serialised as a condensed json file
3. A Scala.js compiled search frontend gets added to your site
4. Index is served as a `.js` file as well
5. Everything comes together and now you have search.

Search adds the following paths to your site:

```text
assets/search-index.js
    ^--copy-of--> /tmp/5284770261000098655.tmp
assets/search.js
    ^--copy-of--> /tmp/2571604987094206986.tmp
```

# Algorithm

Right now we use normalised TF-IDF which was optimised for "please just work"
metric.

I will revise it and benchmark it against some TREC collections.

It should be good enough.

# More information

I'm hoping to write a blog post about it soon-ish.

