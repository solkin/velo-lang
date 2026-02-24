# Classes

## Class Definition

```velo
class Point(int x, int y) {
    # Class fields
    int xCoord = x;
    int yCoord = y;
    
    # Methods
    func distance() float {
        (xCoord * xCoord + yCoord * yCoord).float.sqrt();
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

**Important:** Class fields are read-only from outside the class. To modify fields, you must use class methods.

```velo
Point p = new Point(10, 20);
int x = p.xCoord;        # Reading field (allowed)
# p.xCoord = 15;         # ERROR: cannot modify field from outside
p.move(5, 5);            # Calling method to modify (correct)
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
        value;
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
        a
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
        value;
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
        r;
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
        new Vector(x + other.x, y + other.y);
    };

    operator [](int index) int {
        if (index == 0) then x else y;
    };
};

Vector a = new Vector(1, 2);
Vector b = new Vector(3, 4);
Vector sum = a + b;          # Vector(4, 6)
int first = a[0];            # 1
```

For the full list of overloadable operators and detailed usage, see [Operator Overloading](24-operator-overloading.md).

---

[Previous: Tuples ←](12-tuples.md) | [Next: Extension Functions →](14-extension-functions.md)

