# Extension Functions

Extension functions allow adding methods to existing types:

```velo
# Extension for int
ext(int a) max(int b) int {
    return if (a > b) then a else b;
};

# Extension for str
ext(str a) insert(int index, str s) str {
    return a.sub(0, index).con(s).con(a.sub(index, a.len()));
};

# Usage
int maxValue = 5.max(10);  # 10
str result = "Hello".insert(5, " World");  # "Hello World"
```

## No-Argument Extensions

An extension with no parameters is still called with `()`. Like every method and
conversion, the parentheses are **required** — bare access is only for stored
fields:

```velo
ext(bool b) str() str {
    return if b then "true" else "false"
};

ext(int n) double() int {
    return n * 2
};

# Usage — parentheses are required:
bool flag = true;
str s = flag.str();    # "true"

int x = 5;
int d = x.double();    # 10
# int bad = x.double;  # ERROR: 'double' is a function; call it with parentheses: double()
```

## Extension for Classes

```velo
ext(Terminal t) printInt(int a) void {
    t.println(a.str());
};

Terminal term = new Terminal();
term.printInt(42);  # Will print "42"
```

---

[Previous: Classes ←](13-classes.md) | [Next: Native Classes →](15-native-classes.md)

