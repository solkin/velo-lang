# Language Features

## Logical and bitwise operators

Velo has full logical operators with short-circuit evaluation, alongside
bitwise operators on integers:

- **`&&`, `||`** — logical AND / OR, short-circuit.
- **`!`** — logical NOT (`!ready`).
- **`&`, `|`, `^`** — bitwise AND / OR / XOR on `int`/`long`. On booleans `&`/`|`
  act as short-circuit aliases of `&&`/`||`, but prefer `&&`/`||` for clarity.

There are **no shift operators**; use the `x.shl(n)` / `x.shr(n)` methods.

See [Operators](05-operators.md) for the full table.

## Loop control

`break` and `continue` work inside `while`, `for`-range and `for`-each loops.

```velo
for i in 0..10 {
    if (i == 3) { continue }
    if (i > 6)  { break }
    term.println(i.str())
}
```

## Return values

`return` is **mandatory and explicit** — there is no implicit "last expression
is the result". A non-`void` function must `return` on every path:

```velo
func add(int a, int b) int {
    return a + b
}
```

## The `let` construct

`let name = expr` declares an **immutable, type-inferred** local. It is the
preferred way to bind a local you do not reassign; the type comes from the
initializer:

```velo
let result = calculate()     # type inferred, cannot be reassigned
term.println(result.str())

let name = "Velo"            # str
let count = 0                # int
```

For a mutable variable, declare it with an explicit type instead
(`int count = 0`).

---

[Previous: Running Programs ←](18-running-programs.md) | [Next: Best Practices →](20-best-practices.md)
