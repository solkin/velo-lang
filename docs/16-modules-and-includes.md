# Modules and Includes

## Including Modules

Use the `include` directive to include other files:

```velo
include "lang/bool.vel";       # bool extension functions

# Now you can use what the included module provides
Terminal term = new Terminal();
term.println(true.str);        # "true"
```

## Standard Modules

Velo Lang ships several modules in the `lang/` directory — real Velo code you
include to use:

- `bool.vel` — Boolean extensions (`str`, `int`, `not`)
- `int.vel` — Integer extensions (`abs`, `neg`, `format`)
- `str.vel` — String extensions (`bytes`)
- `array.vel` — Array extensions (`str`)
- `map.vel` — Generic hash map
- `base64.vel` — BASE64 encoding/decoding

The native classes Terminal, Time, Http, FileSystem and Socket need **no**
include: the runtime provides them and the compiler knows their types from
the registration (see [Native Classes](15-native-classes.md)). The example
above includes `bool.vel` for its extension functions and constructs a
`Terminal` directly.

---

[Previous: Native Classes ←](15-native-classes.md) | [Next: Standard Library →](17-standard-library.md)

