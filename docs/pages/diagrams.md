---
id: diagrams
title: Diagrams with D2
---


Subatomic can render diagrams using [D2](https://github.com/terrastruct/d2), all you need is to use a snippet in your markdown like this:

````text
```d2:sample-1
direction: right
dogs -> cats -> mice: chase
replica 1 <-> replica 2
a -> b: To err is human, to moo bovine {
  source-arrowhead: 1
  target-arrowhead: * {
    shape: diamond
  }
}
cats -- a
```
````

where `sample-1` is the name of the diagram (this field is required).

The resulting diagram will be placed under `assets/d2-diagrams/sample-1.svg` and the entire code block will be replaced with 
with an image.

```d2:sample-1
direction: right
dogs -> cats -> mice: chase
replica 1 <-> replica 2
a -> b: To err is human, to moo bovine {
  source-arrowhead: 1
  target-arrowhead: * {
    shape: diamond
  }
}
cats -- a
```

## Passing arguments to d2

You can pass any arguments to the CLI tool by adding them to the code fence, separated by `:`.

For example, here's how you can select ELK rendering engine and change the theme:

````text
```d2:sample-2:--layout=elk:--theme=100
direction: right
dogs -> cats -> mice: chase
replica 1 <-> replica 2
a -> b: To err is human, to moo bovine {
  source-arrowhead: 1
  target-arrowhead: * {
    shape: diamond
  }
}
cats -- a
```
````

```d2:sample-2:--layout=elk:--theme=100
direction: right
dogs -> cats -> mice: chase
replica 1 <-> replica 2
a -> b: To err is human, to moo bovine {
  source-arrowhead: 1
  target-arrowhead: * {
    shape: diamond
  }
}
cats -- a
```

## Embedded diagrams

If you want to embed the diagrams directly into the page, without generating a stable 
file in your assets, use the `d2-embed` language and remove the name.

For example:

````
```d2-embed:--layout=elk:--theme=100
shape: sequence_diagram
alice -> bob: What does it mean\nto be well-adjusted?
bob -> alice: The ability to play bridge or\ngolf as if they were games.
```
````


```d2-embed:--layout=elk:--theme=100
shape: sequence_diagram
alice -> bob: What does it mean\nto be well-adjusted?
bob -> alice: The ability to play bridge or\ngolf as if they were games.
```

