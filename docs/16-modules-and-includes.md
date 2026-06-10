# Modules and Includes

## Including Modules

Use the `include` directive to include other files:

```velo
include "lang/terminal.vel";   # the conventional `term` global
include "lang/bool.vel";       # bool extension functions

# Now you can use what the included modules provide
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
- `terminal.vel` — defines the conventional `term` global

The native classes Terminal, Time, Http, FileSystem and Socket need **no**
include: the runtime provides them and the compiler knows their types from
the registration (see [Native Classes](15-native-classes.md)). `terminal.vel`
is included above only because it defines the `term` global as a convenience.

---

[Previous: Native Classes ←](15-native-classes.md) | [Next: Standard Library →](17-standard-library.md)

