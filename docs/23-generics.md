# Generics

Velo supports generic types for classes and functions, allowing you to write type-safe, reusable code. Generics are a compile-time feature — type parameters are erased at the VM level, so there is no runtime overhead.

## Generic Classes

### Declaration

Use square brackets `[T, U, ...]` after the class name to declare type parameters:

```velo
class Box[T](T value) {
    func get() T {
        value;
    };
    func set(T newValue) void {
        value = newValue;
    };
};
```

Type parameters can be used as field types, method parameter types, and return types within the class body.

### Instantiation

Specify type arguments when declaring the variable type and after `new`:

```velo
Box[int] intBox = new Box[int](42);
Box[str] strBox = new Box[str]("hello");
```

### Accessing Members

When accessing fields or calling methods on a generic instance, the return types are automatically resolved to the concrete types:

```velo
Box[int] b = new Box[int](10);
int x = b.get();    # Returns int, not T
b.set(20);          # Accepts int, not any type
```

### Multiple Type Parameters

```velo
class Pair[T, U](T first, U second) {
    func getFirst() T {
        first;
    };
    func getSecond() U {
        second;
    };
};

Pair[int, str] p = new Pair[int, str](42, "hello");
int n = p.getFirst();    # 42
str s = p.getSecond();   # "hello"
```

### Nested Generics

Type parameters work with composite types like arrays:

```velo
class Container[T]() {
    array[T] items = new array[T](0);
    int size = 0;

    func add(T item) void {
        items = items.plus(item);
        size = size + 1;
    };

    func get(int index) T {
        items[index];
    };

    func getSize() int {
        size;
    };
};

Container[str] c = new Container[str]();
c.add("x");
c.add("y");
str item = c.get(0);    # "x"
```

### Generic Class Composition

A generic class can use another generic class in its fields, with partial or full type argument substitution:

```velo
class Entry[K, V](K key, V val) {
    func getKey() K { key; };
    func getVal() V { val; };
};

class Registry[V]() {
    array[Entry[str, V]] entries = new array[Entry[str, V]](0);

    func put(str key, V value) void {
        Entry[str, V] e = new Entry[str, V](key, value);
        entries = entries.plus(e);
    };

    func getByIndex(int index) V {
        entries[index].getVal();
    };
};

Registry[int] reg = new Registry[int]();
reg.put("a", 100);
int val = reg.getByIndex(0);    # 100
```

Here `Registry[V]` uses `Entry[str, V]` — the key type is fixed as `str`, while the value type `V` is passed through from the outer class.

## Generic Functions

### Declaration

Use square brackets after the function name to declare type parameters:

```velo
func identity[T](T value) T {
    value;
};
```

### Type Inference

Unlike generic classes, generic functions **infer type arguments automatically** from the call arguments — you don't need to specify them explicitly:

```velo
int x = identity(42);       # T inferred as int
str s = identity("hello");  # T inferred as str
```

The compiler matches argument types against parameter types to deduce what each type parameter should be. This works with composite types too:

```velo
func first[T](array[T] items) T {
    items[0];
};

array[int] nums = new array[int]{10, 20, 30};
int n = first(nums);    # T inferred as int from array[int]
```

### Multiple Type Parameters

Generic functions can have multiple type parameters and return generic class instances:

```velo
func makePair[A, B](A a, B b) Pair[A, B] {
    new Pair[A, B](a, b);
};

Pair[int, str] p = makePair(7, "seven");
int n = p.getFirst();     # 7
str s = p.getSecond();    # "seven"
```

Both `A` and `B` are inferred from the arguments — `A = int` from `7`, `B = str` from `"seven"`.

## Generic Methods

Methods inside classes can have their own type parameters, independent of the class-level type parameters.

### Generic Method in a Non-Generic Class

```velo
class Utils() {
    func wrap[T](T value) Box[T] {
        new Box[T](value);
    };
};

Utils u = new Utils();
Box[int] bi = u.wrap(42);       # T inferred as int
Box[str] bs = u.wrap("test");   # T inferred as str
```

### Generic Method in a Generic Class

A generic method can introduce its own type parameter alongside the class-level parameters:

```velo
class Mapper[T]() {
    array[T] items = new array[T](0);

    func add(T item) void {
        items = items.plus(item);
    };

    func transform[U](func[U] fn) array[U] {
        items.map(fn);
    };
};

func strLen(int idx, str item) int {
    item.len;
};

Mapper[str] m = new Mapper[str]();
m.add("hi");
m.add("world");
array[int] lengths = m.transform(strLen);    # U inferred as int
```

Here `T` comes from the class (`str`) and `U` comes from the method, inferred as `int` from the callback's return type.

## Type Safety

Generic types enforce strict type checking at two levels:

### Inside the Generic Body

Within a generic class or function, a type parameter `T` is treated as an opaque type. You can only use `T` where `T` is expected — the compiler prevents mixing `T` with concrete types:

```velo
class Box[T](T value) {
    func get() T { value; };       # OK: T matches T
    # func bad() int { value; };   # ERROR: T is not int
};
```

### At the Usage Site

When concrete types are provided, the compiler checks that all arguments and return values match:

```velo
Box[int] b = new Box[int](42);      # OK: 42 is int
# Box[int] b = new Box[int]("no");  # ERROR: "no" is not int
```

Type arguments must be consistent between the declaration and `new`:

```velo
# Box[str] b = new Box[int](42);    # ERROR: Box[int] != Box[str]
```

Method arguments are also checked against the resolved types:

```velo
Box[str] b = new Box[str]("ok");
# b.set(42);                        # ERROR: int differs from required str
```

## Limitations

- **No type parameter constraints** — type parameters are unconstrained (bounded by `any`). You cannot specify `T extends SomeType`.
- **No property access on type parameters** — since `T` is opaque, you cannot call type-specific properties (like `.str`) on values of type `T` inside the generic body.
- **No variance** — there is no `in`/`out` variance annotation.
- **Type arguments required for classes** — unlike functions, generic classes require explicit type arguments at both the declaration and `new` site.

---

[Previous: Apply Blocks ←](22-apply-blocks.md)
