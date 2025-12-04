# Native Classes

Native classes allow using Java/Kotlin classes in Velo Lang through reflection.

## Defining Native Classes

```velo
native class Terminal() {
    native func print(str text) str;
    native func input() str;
    
    # Regular methods can use native ones
    func println(str text) str {
        print(text.con("\n"));
    };
};

Terminal term = new Terminal();
term.println("Hello, World!");
```

## Requirements for Native Classes

1. The class name in Velo Lang must match the Java/Kotlin class name
2. Method signatures must match
3. The constructor must have the same parameters

---

[Previous: Extension Functions ←](14-extension-functions.md) | [Next: Modules and Includes →](16-modules-and-includes.md)

