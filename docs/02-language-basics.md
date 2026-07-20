# Language Basics

## Hello, World!

```velo
Terminal term = new Terminal()

str hello = "Hello, World!"
term.println(hello)
```

## Comments

Velo Lang uses single-line comments starting with the `#` symbol:

```velo
# This is a comment
str text = "Hello"  # Comment at end of line
```

## Statement Termination

A statement ends at the end of a line — the semicolon is **optional**. Velo
inserts statement terminators automatically (like Go), so both styles compile:

```velo
str a = "one"
str b = "two"        # newline ends each statement — no semicolons needed

str c = "three"; str d = "four"   # use `;` to put several statements on one line
```

Most idiomatic Velo (and the sample programs) omit semicolons. A line is treated
as *continuing* when it clearly cannot end yet — for example when it breaks right
after an operator, a `.`, a comma, or an open bracket. Both styles appear in this
guide; either is fine.

---

[Previous: Introduction ←](01-introduction.md) | [Next: Data Types →](03-data-types.md)

