---
id: diagrams
title: Diagrams with D2
---


Subatomic can render diagrams using [D2](https://github.com/terrastruct/d2), all you need is to use a snippet in your markdown like this:

````text
```d2:sample-1
dogs -> cats -> mice: chase
replica 1 <-> replica 2
a -> b: To err is human, to moo bovine {
  source-arrowhead: 1
  target-arrowhead: * {
    shape: diamond
  }
}
```
````

where `sample-1` is the name of the diagram (this field is required).

The resulting diagram will be placed under `assets/d2-diagrams/sample-1.svg` and the entire code block will be replaced with 
with an image.

```d2:sample-1
dogs -> cats -> mice: chase
replica 1 <-> replica 2
a -> b: To err is human, to moo bovine {
  source-arrowhead: 1
  target-arrowhead: * {
    shape: diamond
  }
}
```
