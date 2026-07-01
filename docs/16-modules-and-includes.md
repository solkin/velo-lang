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
  Rename one of the declarations to resolve it.

## Standard modules

The standard library lives under `std/` — real Velo code you import:

- `std/bool` — Boolean extensions (`str`, `int`, `not`)
- `std/int` — Integer extensions (`abs`, `neg`, `format`)
- `std/str` — String extensions (`bytes`)
- `std/array` — Array extensions (`str`)
- `std/map` — Generic hash map (`dict[K:V]` imports this automatically)
- `std/base64` — BASE64 encoding/decoding
- `std/crc32`, `std/deflate`, `std/zip` — checksums and DEFLATE/ZIP
- `std/random` — `Random`, a bit-for-bit reimplementation of `java.util.Random`

The native classes Terminal, Time, Http, FileSystem and Socket need **no**
import: the runtime provides them and the compiler knows their types from the
registration (see [Native Classes](15-native-classes.md)).

---

[Previous: Native Classes ←](15-native-classes.md) | [Next: Standard Library →](17-standard-library.md)
