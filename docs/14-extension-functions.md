# Extension Functions

Extension functions allow adding methods to existing types:

```velo
# Extension for int
ext(int a) max(int b) int {
    if (a > b) then a else b;
};

# Extension for str
ext(str a) insert(int index, str s) str {
    a.sub(0, index).con(s).con(a.sub(index, a.len));
};

# Usage
int maxValue = 5.max(10);  # 10
str result = "Hello".insert(5, " World");  # "Hello World"
```

## Extension for Classes

```velo
ext(Terminal t) printInt(int a) str {
    t.println(a.str);
};

Terminal term = new Terminal();
term.printInt(42);  # Will print "42"
```

---

[Previous: Classes ←](13-classes.md) | [Next: Native Classes →](15-native-classes.md)

