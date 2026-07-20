# Strings

## Creating Strings

```velo
str greeting = "Hello, World!"
str empty = ""
str multiline = "Line 1\nLine 2"
```

## String Properties and Methods

### `len` — String Length

```velo
str s = "Hello"
int length = s.len()  # 5
```

### `sub(start, end)` — Substring

```velo
str s = "Hello, World!"
str sub = s.sub(7, 12)  # "World"
```

### Concatenation

Use the `+` operator to concatenate strings:

```velo
str a = "Hello"
str b = "World"
str combined = a + ", " + b  # "Hello, World"
```

Alternatively, use the `con(other)` method:

```velo
str combined = a.con(", ").con(b)  # "Hello, World"
```

### Index Access

```velo
str s = "Hello"
int charCode = s[0]  # Character code for 'H'
str firstChar = s[0].char()  # "H"
```

## Type Conversion

Any number becomes a string with `.str()`; a string parses back into a number
with `.int()`, `.long()`, or `.float()`:

```velo
str numStr = 42.str()  # "42"
str piStr  = 3.14.str()  # "3.14"

int   i = "123".int()  # 123
long  l = "5000000000".long()  # 5000000000
float f = "3.14".float()  # 3.14
```

A string that is not a valid number raises an error when parsed.

## String Interpolation

Embed values directly in a string literal with `$name` for a simple variable, or
`${expr}` for any expression:

```velo
str name = "Velo"
int x = 3
term.println("Hello, $name!")  # Hello, Velo!
term.println("x = $x, x + 1 = ${x + 1}")  # x = 3, x + 1 = 4
```

Write `\$` for a literal dollar sign.

---

[Previous: Dictionaries ←](10-dictionaries.md) | [Next: Tuples →](12-tuples.md)

