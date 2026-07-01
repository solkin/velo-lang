# Interfaces

An **interface** is a named set of method signatures — a contract a type satisfies by **shape**, not by declaration. Velo interfaces are *structural* (Go-style): any [class](13-classes.md) — or even a [native class](15-native-classes.md) — that has the right methods satisfies the interface automatically. There is no base class to extend and no `implements` to write.

```velo
interface Shape {
    func area() int;
    func kind() str;
};

# Neither class names `Shape`. They satisfy it because they have its methods.
class Square(int side) {
    func area() int { return side * side; };
    func kind() str { return "square"; };
};

class Rect(int w, int h) {
    func area() int { return w * h; };
    func kind() str { return "rect"; };
};

Shape s = new Square(4);
term.println(s.kind());        # square
s = new Rect(3, 5);
term.println(s.area().str());    # 15
```

Assigning a `Square` to a `Shape` variable is allowed because `Square` provides every method `Shape` declares. The call `s.kind()` is dispatched **dynamically** to the concrete class behind `s` at run time.

## Declaring an interface

An interface body is a list of method **signatures** — a name, parameters and a return type, with no body:

```velo
interface Drawable {
    func draw(Canvas c) void;
    func bounds() int;
};
```

The semicolon after each signature is optional. A method may not have a body inside an interface — only the signature. An interface may reference itself in a signature (e.g. a builder that returns the same interface), because the name is in scope while its methods are parsed.

## Structural satisfaction

A type satisfies an interface when it provides **every** method, each with a matching signature: the same name, the same parameter types in the same order, and the same return type. Matching is exact — there is no implicit conversion or variance on parameters.

```velo
interface Shape { func area() int; };

class Dot() { func draw() int { return 0; }; };

Shape s = new Dot();   # error: Dot has no `area() int` — it does not satisfy Shape
```

A signature that differs only in return type does **not** match:

```velo
interface Shape { func area() int; };
class Square(int side) { func area() str { return "no"; }; };

Shape s = new Square(2);   # error: area returns str, not int
```

> **Declare an interface before the types that satisfy it.** A class is matched against the interfaces in scope when it is compiled, so keep the `interface` ahead of the classes — and the assignments — that rely on it.

## The interface is the whole contract

Through an interface-typed value you may call **only** the methods the interface declares — even if the concrete class has more. The interface is the complete, exclusive view:

```velo
interface Shape { func area() int; };

class Square(int side) {
    func area() int { return side * side; };
    func kind() str { return "square"; };
};

Shape s = new Square(2);
term.println(s.area().str());   # ok
term.println(s.kind());       # error: kind() is not part of Shape
```

This is what makes interface code reusable: a function that takes a `Shape` works for any present or future type that satisfies it, and can rely on exactly the declared methods.

## Explicit conformance

Structural satisfaction alone is enough, but a class may also **declare** the interfaces it means to satisfy, after the constructor parameters:

```velo
interface Shape { func area() int; func kind() str; };

class Square(int side) : Shape {
    func area() int { return side * side; };
    func kind() str { return "square"; };
};
```

This changes nothing at run time — dispatch is identical. What it buys you is **intent and early errors**: if `Square` is missing or mistypes a method, the error surfaces at the class declaration (naming the missing methods) instead of at the first place you try to use it as a `Shape`. A class may conform to several interfaces, comma-separated: `class Square(int side) : Shape, Drawable { ... }`.

```velo
interface Shape { func area() int; func kind() str; };
class Square(int side) : Shape { func area() int { return side * side; }; };
# error: Square declares it conforms to Shape but is missing kind()
```

## Interfaces as values

An interface is an ordinary type: use it for variables, parameters, return types and array elements. Every call through it dispatches to the element's concrete class.

```velo
interface Shape { func area() int; func kind() str; };

# A function that works for any Shape.
func describe(Shape s) str { return s.kind().con("(").con(s.area().str()).con(")"); };
term.println(describe(new Rect(10, 10)));   # rect(100)

# A heterogeneous array — each element a different concrete class.
array[Shape] shapes = new array[Shape]{ new Square(3), new Rect(2, 5), new Square(4) };
int i = 0;
while (i < shapes.len()) {
    Shape s = shapes[i];
    term.println(s.kind().con(" = ").con(s.area().str()));
    i += 1;
};
# square = 9
# rect = 10
# square = 16
```

## `Self` return type

A method may declare its return type as **`Self`** — "an instance of whatever the concrete type is". It is the key to fluent builders: the chain keeps the concrete type instead of widening to the interface.

```velo
class Counter(int n) {
    func bump() Self { return new Counter(n + 1); };
    func value() int { return n; };
};

Counter c = new Counter(0);
term.println(c.bump().bump().bump().value().str());   # 3
```

`Self` is resolved entirely at compile time to the receiver's type at each call site, so `c.bump()` is a `Counter` and the chain type-checks. `Self` is legal only as a **return type**, never as a parameter — that restriction is what keeps structural satisfaction sound.

An interface can require a `Self`-returning method, and dispatch still flows correctly when the value is interface-typed:

```velo
interface Builder {
    func grow() Self;
    func value() int;
};

class Counter(int n) {
    func grow() Self { return new Counter(n + 1); };
    func value() int { return n; };
};

Builder b = new Counter(10);
term.println(b.grow().grow().value().str());   # 12
```

## Bounded generics

A [generic](23-generics.md) type parameter can be **bounded** by an interface with `[T: Interface]`. Inside the generic, you may call the bound's methods on a `T`; at instantiation, the type argument must satisfy the bound. The bound **must be an interface** — only a structural contract can be checked without runtime type information.

```velo
interface Shape { func area() int; };
class Square(int side) { func area() int { return side * side; }; };
class Rect(int w, int h) { func area() int { return w * h; }; };

class Boxed[T: Shape](T item) {
    func areaOf() int { return item.area(); };   # `area` is available because T: Shape
};

Boxed[Square] a = new Boxed[Square](new Square(5));
term.println(a.areaOf().str());   # 25

# Violating the bound is rejected:
class Dot() { func draw() int { return 0; }; };
Boxed[Dot] bad = new Boxed[Dot](new Dot());   # error: Dot does not satisfy Shape
```

## Native classes satisfy interfaces too

This is where structural typing pays off most. A registered [native class](15-native-classes.md) — a host (JVM) object exposed to Velo — satisfies a Velo interface **structurally**, with no annotation, wrapper or change on the host side. If the host class has methods with the right names and signatures, it *is* a `Shape`:

```velo
# `Widget` is a host class registered with the runtime, with methods
# `area() int` and `kind() str` (and a fluent `padding(int) Self`).
interface Shape { func area() int; func kind() str; };

Widget w = new Widget("hello");
Shape s = w;                   # the native handle satisfies Shape
term.println(s.kind());        # widget:hello
term.println(s.area().str());    # 5
```

Velo instances and native handles can sit behind the **same** interface and dispatch the same way: when the receiver is a Velo class the call enters its method, and when it is a native handle the method is resolved by name and invoked across the native boundary. The `Self` rule applies to host methods too — a fluent native method that returns itself fulfils a `Self`-returning requirement. See the **Card Feed** sample (`velo-android/samples/card-feed`) for an interface that spans Velo classes and native Material3 widgets.

## Interfaces and actors

An interface value is **not** [transferable](26-actors.md#what-crosses-the-boundary) across an [actor](26-actors.md) boundary. An interface is a dynamic-dispatch view whose concrete class is only known at run time, so it cannot be copied to another thread the way a primitive or a [`data class`](28-data-classes.md) can. Declaring an actor method that takes or returns an interface is a compile-time error:

```velo
interface Shape { func area() int; };

actor class Worker() {
    func handle(Shape s) int { return s.area(); };   # error: Shape is not transferable
};
```

To send structured data to an actor, use a transferable type — a primitive, an array/tuple of transferable values, or a `data class`.

## Summary

| Feature | Form | Notes |
|---|---|---|
| Declare | `interface Name { func m(...) T; }` | signatures only, no bodies |
| Satisfy structurally | (nothing) | a class/native with matching methods just works |
| Declare conformance | `class C(...) : Iface { ... }` | optional; documents intent, fails fast |
| Use as a type | variable / parameter / `array[Iface]` | dynamic dispatch to the concrete class |
| Fluent return | `func m() Self` | keeps the concrete type through a chain |
| Bound a generic | `class C[T: Iface](...)` | bound must be an interface |
| Native conformance | (nothing) | host objects satisfy interfaces by shape |
| Actor boundary | — | interfaces are **not** transferable |

---

[Previous: Data Classes ←](28-data-classes.md) | [Next: LLM Guide →](30-llm-guide.md)
