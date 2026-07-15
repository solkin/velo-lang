# Error Handling

A runtime failure in Velo is an **`Error`** — a small value with a
machine-readable `kind` and a human-readable `message`. `try`/`catch` recovers
from one; `throw` raises your own. An error that is never caught stops the
program loudly, exactly as before this feature existed — catching is purely
additive.

```velo
try {
    int n = risky()
    term.println("got $n")
} catch (Error e) {
    term.println("failed (${e.kind}): ${e.message}")
}
```

The `Error` type and the `ERR_*` constants come from `std/error`, which is
**imported automatically** wherever a program uses `try` or `throw` — no explicit
`import` is needed.

## The `Error` value

```velo
data class Error(str kind, str message) {}
```

An `Error` is an immutable [data class](28-data-classes.md): `kind` categorises
the failure, `message` describes it. Because it is a data class it compares by
value and crosses actor and native boundaries like any other.

Branch on `kind` — it is the portable, machine-readable discriminator and is
identical on every backend. `message` is a human-readable diagnostic; for a
built-in runtime failure its exact wording is host-dependent (a division by zero
may read differently from one VM to the next), so log it rather than parse it.

`kind` is a plain string, compared against the `ERR_*` constants rather than
hard-coded literals (the same way you would name any set of constants with a
top-level `let`). The runtime uses these kinds:

| Constant | `kind` | Raised by |
|---|---|---|
| `ERR_ARITHMETIC` | `"arithmetic"` | division or remainder by zero |
| `ERR_BOUNDS` | `"bounds"` | array / string index out of range |
| `ERR_NULL` | `"null"` | null pointer dereference |
| `ERR_NATIVE` | `"native"` | a native (host) call threw |
| `ERR_ACTOR` | `"actor"` | an actor failed, surfaced through `await` |
| `ERR_GENERIC` | `"generic"` | `throw "text"` with no explicit kind |

By convention every error-kind constant is named `ERR_*`. Define your own the
same way, and use it for both `throw` and the matching `catch` check so the two
can never drift:

```velo
let ERR_NOT_FOUND = "not_found"

try {
    fetch(id)
} catch (Error e) {
    if (e.kind == ERR_NOT_FOUND) {
        showPlaceholder()
    } else {
        throw e                 # not ours — re-raise for an outer handler
    }
}
```

## Catching: `try` / `catch`

`try` runs a block; if any catchable error occurs — anywhere in the block or in
a function it calls — control jumps to the `catch` block with the `Error` bound
to the named variable. That variable is visible only inside the `catch`.

```velo
try {
    array[int] a = new array[int](2)
    int x = a[9]                # ERR_BOUNDS
} catch (Error e) {
    term.println(e.kind)        # bounds
}
```

The caught type is always `Error`. `try`/`catch` is a statement — it produces no
value (unlike `if`, which can be an expression). A `try` with no error simply
skips its `catch`.

## Throwing: `throw`

`throw` raises an `Error`:

```velo
throw new Error(ERR_NOT_FOUND, "item $id is missing")
```

As a shorthand, `throw "text"` on a bare string literal is
`throw new Error(ERR_GENERIC, "text")`:

```velo
throw "something went wrong"    # kind is "generic"
```

Inside a `catch`, `throw e` re-raises the caught error for an outer `try` to
handle.

## What is and isn't catchable

**Catchable** — routed to the nearest `try`:

- a `throw`;
- a runtime failure (arithmetic, bounds, null dereference);
- a failing **native** call;
- an **actor** failure observed at an `await` — `try` around the `await` catches
  it, with `kind == ERR_ACTOR`:

```velo
try {
    Data d = await async worker.load(id)
    render(d)
} catch (Error e) {
    render(fallback())          # the actor threw; recover here
}
```

**Not catchable** — a user `halt` stops the program unconditionally and no `try`
intercepts it (a future execution-budget limit will behave the same way).

## Uncaught errors are fatal

An error with no enclosing `try` terminates the program and prints a diagnostic —
the pre-existing "every runtime error is fatal" behaviour. This is why a
fire-and-forget callback whose body throws (with no observer) is program-fatal:
there is nothing to catch it. Wrap the body in a `try` to recover.

---

[Previous: Grammar ←](31-grammar.md) | [Back to README →](README.md)
