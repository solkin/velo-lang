# Variables and Assignment

## Variable Declaration

```velo
# With explicit type
int x = 10
str name = "Velo"

# With any type
any value = 42
value = "Hello"
```

> Semicolons are optional — a newline ends a statement (see [Language
> Basics](02-language-basics.md#statement-termination)). This guide mixes both
> styles; write whichever you prefer.

## Immutable Inferred Locals (`let`)

`let name = expr` declares a local whose type is **inferred** from the
initializer and which is **immutable** — it cannot be reassigned. Prefer `let`
for locals you don't mutate:

```velo
let x = 42               # int, inferred
let greeting = "Hello"   # str, inferred
# x = 43                 # ERROR: let bindings are immutable
```

Use an explicit typed declaration (`int x = 10`) when you need to reassign the
variable.

## Assignment

```velo
int a = 5
a = 10  # Change value
a = a + 1  # Arithmetic operations
```

## Compound Assignment

Velo supports compound assignment operators for concise updates:

```velo
int a = 10

a += 5  # Same as: a = a + 5
a -= 3  # Same as: a = a - 3
a *= 2  # Same as: a = a * 2
a /= 4  # Same as: a = a / 4
a %= 3  # Same as: a = a % 3
```

---

[Previous: Data Types ←](03-data-types.md) | [Next: Operators →](05-operators.md)

