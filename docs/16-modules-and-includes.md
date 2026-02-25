# Modules and Includes

## Including Modules

Use the `include` directive to include other files:

```velo
include "lang/terminal.vel";
include "lang/filesystem.vel";

# Now you can use classes from included modules
Terminal term = new Terminal();
FileSystem fs = new FileSystem();
```

## Standard Modules

Velo Lang includes several standard modules in the `lang/` directory:

- `terminal.vel` — Terminal operations (input/output)
- `time.vel` — Time operations
- `http.vel` — HTTP requests
- `filesystem.vel` — File system operations
- `socket.vel` — TCP socket communication
- `base64.vel` — BASE64 encoding/decoding
- `bool.vel` — Boolean extensions (`str`, `int`, `not`)
- `int.vel` — Integer extensions (`abs`, `neg`, `format`)
- `str.vel` — String extensions (`bytes`)
- `array.vel` — Array extensions (`str`)
- `map.vel` — Generic hash map

---

[Previous: Native Classes ←](15-native-classes.md) | [Next: Standard Library →](17-standard-library.md)

