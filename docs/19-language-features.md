# Language Features

## Missing Operators

- **No unary `!`** — use `== false` for negation
- **No `&&`** — use `&`, which always evaluates both operands
- **No `||`** — use `|`
- **No `break` and `continue`** — use conditional statements

## Return Values

In functions, the last expression automatically becomes the return value:

```velo
func add(int a, int b) int {
    a + b;  # This value will be returned
};
```

## The `let` Construct

The `let` construct allows declaring local variables:

```velo
let(any result = calculate()) {
    term.println(result.str);
};
```

---

[Previous: Running Programs ←](18-running-programs.md) | [Next: Best Practices →](20-best-practices.md)

