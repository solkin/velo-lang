# Data Classes

A **data class** is an immutable *value type*. Where a plain [`class`](13-classes.md) is a mutable object you share by reference, a `data class` is a small bundle of data that is compared **by value** and **copied** whenever it crosses a thread or native boundary. It is the natural way to move structured data between [actors](26-actors.md) and to and from [native code](15-native-classes.md).

```velo
data class Point(int x, int y) {
    func sum() int { x + y; };
};

Point p = new Point(3, 4);
term.println(p.x.str());       # 3
term.println(p.sum().str());   # 7
```

## The value-type contract

A data class is deliberately rigid — that rigidity is what makes copying and comparing it well-defined:

- **State is exactly the constructor parameters.** The body may declare methods, but not extra fields. There are no computed or mutable members.
- **Fields are immutable.** They are set once at construction; reassigning one (even inside a method) is a compile-time error.
- **Every field must be transferable** — a primitive, an `array`/`tuple` of transferable values, another `data class`, or an `actor[T]` handle. This guarantees the whole value can always cross a boundary.
- **No generics.** A data class is not parameterised.

```velo
data class Bad(int x) {
    int y = 10;        # error: a data class body may only declare methods
};
```

## Equality by value

Two data class values are equal when they are the same class and all their fields are equal, recursively (nested data classes and arrays compare deeply). This is built in — no operator overload required:

```velo
data class Point(int x, int y) {};

Point a = new Point(1, 2);
Point b = new Point(1, 2);
term.println(if (a == b) then "equal" else "different");   # equal
```

Plain classes keep identity (reference) equality; only data classes compare structurally.

## Across actors: copied by value

A data class is [transferable](26-actors.md#what-crosses-the-boundary): passing one to an actor, or returning one, hands over an independent **copy**. The receiver cannot observe later changes on the sender's side — there are none to observe, because the value is immutable — and no mutable state is ever aliased across threads.

```velo
data class Point(int x, int y) {
    func sum() int { x + y; };
};

actor class Geometry() {
    func translate(Point p, int dx, int dy) Point {
        new Point(p.x + dx, p.y + dy);
    };
};

actor[Geometry] geo = new Geometry();
Point moved = await geo.translate(new Point(10, 20), 5, 6);
term.println(moved.x.str());      # 15
term.println(moved.sum().str());  # 41
```

The copy is a deep one: nested data classes and arrays of data classes travel too.

## Across the native boundary: by value

A data class can also be passed to and returned from [native methods](15-native-classes.md). The host registers the counterpart JVM type — a Kotlin `data class` works as-is (one constructor taking the fields in order; `getX()` accessors):

```kotlin
data class NativePoint(val x: Int, val y: Int)

class Geometry {
    fun translate(p: NativePoint, dx: Int, dy: Int) = NativePoint(p.x + dx, p.y + dy)
}

val natives = NativeRegistry()
    .registerData("Point", NativePoint::class)   // Velo `data class Point` <-> NativePoint
    .register(Geometry::class)
```

```velo
data class Point(int x, int y) {};

Geometry g = new Geometry();
Point moved = g.translate(new Point(3, 4), 10, 20);
term.println(moved.x.str());   # 13
```

Velo → JVM reads the fields and calls the host constructor; JVM → Velo reads the host value's fields (by name) and rebuilds the Velo value. Unlike a [native class](15-native-classes.md) — which travels as an opaque handle — a data class is marshalled field by field, so both sides hold plain, independent values.

## When to use which

| You want… | Use |
|---|---|
| An immutable bundle of data to move between actors / native | `data class` |
| A mutable object with shared identity within one thread | `class` |
| A live service with private state on its own thread | `actor class` |
| An opaque host object you only call methods on | a registered native class |

---

[Previous: Callbacks ←](27-callbacks.md) | [Next: Interfaces →](29-interfaces.md)
