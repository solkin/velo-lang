# Modules and Imports

Every `.vel` file is a module. Use `import` to bring another module's top-level
declarations (functions, classes, extensions) into scope.

## Importing

```velo
import "std/bool"                # standard-library module (no .vel needed)
import "util/hash"               # a file util/hash.vel, relative to THIS file
import "./sibling"               # a sibling module

Terminal term = new Terminal()
term.println(true.str())         # "true" — from std/bool
```

Rules:

- **The `.vel` extension is optional** — `import "std/map"` loads `std/map.vel`.
- **`std/` names** resolve to the bundled standard library.
- **Any other path** resolves **relative to the importing file**, so code can be
  organised in sub-directories (a module in `util/` can `import "./helper"`).
- **Each module is loaded once** per compilation, keyed by its real path — the
  same file reached two ways (directly and transitively) is not re-imported.
- **A name clash between modules is a compile error**, not a silent override.
  Rename one of the declarations to resolve it, or import under a namespace (below).

## Namespaced imports (`as`)

`import "path" as ns` brings a module's declarations in under the prefix `ns`
instead of into the top-level scope. Reach every top-level function and class of
that module qualified as `ns.name`:

```velo
import "geom" as g
import "shapes" as s          # both modules may define `area` — no clash

term.println(g.area(3).str())
g.Point p = new g.Point(1, 2) # a namespaced class is a type AND a constructor
```

Rules:

- **Members are reached qualified** — `ns.func(...)`, `new ns.Class(...)`, and
  the type `ns.Class` (e.g. `g.Point p = ...`). Bare `func()` / `Class` do not
  see a namespaced module's names.
- **Namespacing sidesteps clashes** — two modules that both export `area` can be
  imported `as a` / `as b` and used as `a.area()` / `b.area()`.
- **Inside the module nothing changes** — its own functions, methods, and class
  references resolve to each other normally; the namespace is only how *callers*
  see it.
- **One alias per module** — a module already loaded (by any import) keeps its
  first binding; importing the same file again under a different alias is a
  no-op.

## Standard modules

The standard library lives under `std/` — real Velo code you import:

- `std/bool` — Boolean extensions (`int`; `bool.str()` is a built-in, no import needed)
- `std/int` — Integer extensions (`abs`, `format`)
- `std/str` — String extensions (`bytes`)
- `std/array` — Array extensions (`str`)
- `std/map` — Generic hash map (`dict[K:V]` imports this automatically)
- `std/error` — the `Error` type and `ERR_*` codes (auto-imported by `try`/`catch`/`throw`; see [Error Handling](32-error-handling.md))
- `std/base64` — Base64 encoding/decoding
- `std/crc32` — CRC-32 checksum
- `std/deflate` — raw DEFLATE compression
- `std/zip` — single-entry ZIP archives
- `std/random` — `Random`, a bit-for-bit reimplementation of `java.util.Random`

The native classes Terminal, Time, Http, FileSystem and Socket need **no**
import: the runtime provides them and the compiler knows their types from the
registration (see [Native Classes](15-native-classes.md)).

---

[Previous: Native Classes ←](15-native-classes.md) | [Next: Standard Library →](17-standard-library.md)
