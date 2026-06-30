# Classes

## Class Definition

```velo
class Point(int x, int y) {
    # Class fields
    int xCoord = x;
    int yCoord = y;
    
    # Methods
    func distance() float {
        return (xCoord * xCoord + yCoord * yCoord).float.sqrt();
    };
    
    func move(int dx, int dy) void {
        xCoord = xCoord + dx;
        yCoord = yCoord + dy;
    };
};
```

## Creating Instances

```velo
Point p = new Point(10, 20);
```

## Accessing Fields and Methods

**Parentheses rule (one obvious way):** anything that *computes* — a method, a
built-in conversion (`x.str()`, `arr.len()`), an extension call — is written
with `()`, even when it takes no arguments. Only a *stored field* (a class
field, a tuple element) is accessed bare. Reading a field with `()` or calling
a method without it is a compile error.

**Important:** Class fields are read-only from outside the class. To modify fields, you must use class methods.

```velo
Point p = new Point(10, 20);
int x = p.xCoord;        # Reading a field — no parens
# p.xCoord = 15;         # ERROR: cannot modify field from outside
p.move(5, 5);            # Calling a method — parens (even if it took none)
int d = p.dist();        # A zero-argument method still needs ()
```

Example class with methods for modifying fields:

```velo
class Counter() {
    int value = 0;
    
    # Method to modify field from outside
    func increment() void {
        value = value + 1;
    };
    
    func add(int amount) void {
        value = value + amount;
    };
    
    func getValue() int {
        return value;
    };
};

Counter c = new Counter();
int current = c.value;     # Reading: OK
c.increment();             # Modifying through method: OK
# c.value = 10;            # Direct modification: ERROR
```

## Nested Classes

```velo
class LinkedList() {
    class Item(Item prev, any value) {};
    
    Item start;
    Item end;
    int length = 0;
    
    func add(any value) void {
        end = new Item(end, value);
        if (length == 0) {
            start = end;
        };
        length = length + 1;
    };
};
```

## Constructor

The class body is executed as a constructor:

```velo
class ValueStorage(int h, str b) {
    int a = h + 1;  # Initialization in constructor
    
    func getValue() int {
        return a
    };
};
```

## Field Mutability

**Important:** Class fields can only be modified inside class methods. From outside the class, fields are read-only.

```velo
class Counter() {
    int value = 0;
    
    # Method to modify field from outside
    func increment() void {
        value = value + 1;  # Modifying inside method: OK
    };
    
    func add(int amount) void {
        value = value + amount;  # Modifying inside method: OK
    };
    
    func getValue() int {
        return value;
    };
};

Counter c = new Counter();
int current = c.value;     # Reading field: OK
c.increment();             # Modifying through method: OK
# c.value = 10;            # Direct modification from outside: ERROR
```

Another example:

```velo
class Random(int seed) {
    int previous = 0;
    
    func setSeed(int seed) void {
        previous = seed;  # Modifying field inside method: OK
    };
    
    func next() int {
        int r = previous * 2;
        previous = r;     # Modifying field inside method: OK
        return r;
    };
};

Random r = new Random(10);
int val = r.previous;     # Reading field: OK
r.setSeed(20);            # Modifying through method: OK
# r.previous = 30;        # Direct modification from outside: ERROR
```

## Operator Overloading

Classes can define custom behavior for built-in operators using the `operator` keyword:

```velo
class Vector(int x, int y) {
    operator +(Vector other) Vector {
        return new Vector(x + other.x, y + other.y);
    };

    operator [](int index) int {
        return if (index == 0) then x else y;
    };
};

Vector a = new Vector(1, 2);
Vector b = new Vector(3, 4);
Vector sum = a + b;          # Vector(4, 6)
int first = a[0];            # 1
```

For the full list of overloadable operators and detailed usage, see [Operator Overloading](24-operator-overloading.md).

## Data classes

The classes above are mutable objects shared by reference. For an **immutable value type** — compared by value and copied across actor and native boundaries — use a [`data class`](28-data-classes.md) instead.

## Interfaces

A class can be used through an [interface](29-interfaces.md) — a contract of method signatures it satisfies by shape, with no base class to extend. A class may also name an interface explicitly after its constructor parameters (`class Square(int side) : Shape { ... }`) to document intent and get errors at the declaration. A method whose return type is [`Self`](29-interfaces.md#self-return-type) keeps the concrete type through a fluent chain.

---

[Previous: Tuples ←](12-tuples.md) | [Next: Extension Functions →](14-extension-functions.md)

