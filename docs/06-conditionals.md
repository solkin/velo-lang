# Conditional Statements

## If-then-else (Expression)

```velo
int a = 5;
str result = if a == 2 then "two" else "not two";
```

## If-then-else (Block)

```velo
int a = 5;
str result = if a == 2 then {
    "two"
} else {
    "not two"
};
```

## Nested Conditionals

```velo
int score = 85;
str grade = if score >= 90 then {
    "A"
} else if score >= 80 then {
    "B"
} else if score >= 70 then {
    "C"
} else {
    "F"
};
```

## Pattern matching with `when`

`when` is the multi-way branch — a switch and a pattern match in one form. Like
`if`, it is an **expression**: each arm's body yields a value (a block yields its
last expression, no `return`). Arms are `pattern -> body`, one per line (no
commas), and `else ->` is the catch-all.

Over a **primitive** (`int`, `byte`, `long`, `float`, `str`, `bool`) the patterns
are literals compared with `==` — the same comparison `if` uses (matching a
`float` is exact equality, with the usual precision caveat). An `else` is
required, except a `bool` that covers both `true` and `false` (a closed domain):

```velo
str name = when day {
    0 -> "Sunday"
    6 -> "Saturday"
    else -> "weekday"
}

str answer = when ok {
    true  -> "yes"
    false -> "no"
}
```

### Sum types — `enum`

An `enum` is a **closed set of variants**, each a value-type record (immutable,
compared by value, transferable across actors — like a [`data class`](28-data-classes.md)).
A variant may carry fields, and may reference the enum recursively:

```velo
enum Expr {
    Lit(int n)
    Add(Expr l, Expr r)
    Neg(Expr e)
}

Expr program = new Add(new Lit(3), new Neg(new Lit(1)))
```

A `when` over an enum matches variants and binds their fields positionally. The
compiler checks the arms are **exhaustive** — every variant must be covered, or
an `else` provided — so a forgotten case is a compile error, not a runtime bug:

```velo
func eval(Expr e) int {
    return when e {
        Lit(n)    -> n
        Add(l, r) -> eval(l) + eval(r)
        Neg(x)    -> 0 - eval(x)
    }
}
```

A variant with no fields is written bare in a pattern and constructed with `new`:

```velo
enum Color { Red, Green, Blue }

str hex = when c {
    Red   -> "#f00"
    Green -> "#0f0"
    Blue  -> "#00f"
}
# ... constructed as: Color c = new Green()
```

### What `when` matches — and what it doesn't

A pattern goes **one level deep**: a variant name and its immediate field
bindings. There is deliberately no nested-pattern matching and no arm guard —
both are expressible with what the language already has, at no extra cost:

```velo
# Nested match: bind one level, then match the field with another `when`.
when e {
    Add(l, r) -> when l {
        Lit(a) -> a
        else   -> 0
    }
    Lit(n) -> n
    Neg(x) -> 0 - eval(x)
}

# A condition on a variant: use `if` inside the arm body.
when e {
    Lit(n) -> if n > 0 then "positive" else "non-positive"
    Add(l, r) -> "sum"
    Neg(x) -> "negated"
}
```

Enums are not generic in this version (`enum Option[T] { ... }` is not
supported) — a variant's fields take concrete types.

Arms are separated by a newline, or a comma. Prefer commas when a pattern begins
with an operator (a negative literal), so it is not read as a continuation of the
previous arm:

```velo
str s = when n { 1 -> "one", -1 -> "neg", else -> "other" }
```

---

[Previous: Operators ←](05-operators.md) | [Next: Loops →](07-loops.md)

