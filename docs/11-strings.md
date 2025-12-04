# Strings

## Creating Strings

```velo
str greeting = "Hello, World!";
str empty = "";
str multiline = "Line 1\nLine 2";
```

## String Properties and Methods

### `len` — String Length

```velo
str s = "Hello";
int length = s.len;  # 5
```

### `sub(start, end)` — Substring

```velo
str s = "Hello, World!";
str sub = s.sub(7, 12);  # "World"
```

### `con(other)` — Concatenation

```velo
str a = "Hello";
str b = "World";
str combined = a.con(", ").con(b);  # "Hello, World"
```

### Index Access

```velo
str s = "Hello";
int charCode = s[0];      # Character code for 'H'
str firstChar = s[0].char; # "H"
```

## Type Conversion

```velo
int num = 42;
str numStr = num.str;     # "42"

str text = "123";
int num = text.int;       # 123
```

---

[Previous: Dictionaries ←](10-dictionaries.md) | [Next: Tuples →](12-tuples.md)

