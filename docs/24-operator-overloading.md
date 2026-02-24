# Operator Overloading

Velo supports operator overloading for user-defined classes using the `operator` keyword. This allows instances of your classes to work with built-in operators like `+`, `-`, `[]`, and others.

## Syntax

Operator declarations are placed inside a class body using the `operator` keyword followed by the operator symbol, parameter list, return type, and body:

```velo
class MyClass() {
    operator +(MyClass other) MyClass {
        # return a new MyClass combining self and other
    };
};
```

## Supported Operators

### Binary Operators

Binary operators take one parameter (the right-hand operand) and return a value:

| Operator | Internal Name | Description          |
|----------|---------------|----------------------|
| `+`      | `op@+`        | Addition             |
| `-`      | `op@-`        | Subtraction          |
| `*`      | `op@*`        | Multiplication       |
| `/`      | `op@/`        | Division             |
| `%`      | `op@%`        | Modulo               |
| `==`     | `op@==`       | Equality             |
| `!=`     | `op@!=`       | Inequality           |
| `<`      | `op@<`        | Less than            |
| `>`      | `op@>`        | Greater than         |
| `<=`     | `op@<=`       | Less than or equal   |
| `>=`     | `op@>=`       | Greater than or equal|

```velo
class Vector(int x, int y) {
    operator +(Vector other) Vector {
        new Vector(x + other.x, y + other.y);
    };

    operator ==(Vector other) bool {
        x == other.x & y == other.y;
    };

    operator <(Vector other) bool {
        x < other.x & y < other.y;
    };
};

Vector a = new Vector(1, 2);
Vector b = new Vector(3, 4);
Vector sum = a + b;          # Vector(4, 6)
bool same = a == a;          # true
bool less = a < b;           # true
```

### Unary Operators

The unary negation operator takes no parameters:

| Operator | Internal Name | Description |
|----------|---------------|-------------|
| `-`      | `op@neg`      | Negation    |

The compiler distinguishes unary `-` from binary `-` by the number of parameters: zero parameters means unary negation, one parameter means binary subtraction.

```velo
class Vector(int x, int y) {
    operator -() Vector {
        new Vector(0 - x, 0 - y);
    };
};

Vector a = new Vector(1, 2);
Vector neg = -a;             # Vector(-1, -2)
Vector dneg = -(-a);         # Vector(1, 2)
```

### Index Operators

Index operators enable bracket-based access on class instances:

| Operator | Internal Name | Description |
|----------|---------------|-------------|
| `[]`     | `op@[]`       | Index read  |
| `[]=`    | `op@[]=`      | Index write |

The read operator `[]` takes one parameter (the index/key) and returns a value. The write operator `[]=` takes two parameters (the index/key and the value to assign):

```velo
class Vector(int x, int y) {
    operator [](int index) int {
        if (index == 0) then x else y;
    };

    operator []=(int index, int value) void {
        if (index == 0) then x = value else y = value;
    };
};

Vector v = new Vector(0, 0);
v[0] = 10;                   # calls operator []=
v[1] = 20;                   # calls operator []=
int first = v[0];            # calls operator [], returns 10
```

## Compound Assignment

Compound assignment operators (`+=`, `-=`, `*=`, `/=`, `%=`) are desugared by the compiler into regular binary operations. For example, `a += b` becomes `a = a + b`. This means defining `operator +` is sufficient for `+=` to work:

```velo
class Vector(int x, int y) {
    operator +(Vector other) Vector {
        new Vector(x + other.x, y + other.y);
    };
};

Vector v = new Vector(1, 2);
v = v + new Vector(10, 10);  # v is now (11, 12)
```

Index-based compound expressions also work:

```velo
Vector v = new Vector(5, 10);
v[0] = v[0] + 15;            # v.x is now 20
```

## Operator Precedence

Overloaded operators follow the same precedence rules as built-in operators. Multiplication binds tighter than addition, comparisons bind looser, and so on:

```velo
# * is evaluated before +, just like with built-in types
Vector result = a + b * c;   # same as a + (b * c)
```

## Left-Operand Dispatch

Operator overloading dispatches on the **left operand** only. The left operand must be a class type with the operator defined. If the left operand is a built-in type, built-in behavior is used regardless of the right operand:

```velo
Vector a = new Vector(1, 2);
Vector sum = a + a;           # OK: left is Vector, dispatches to op@+
# int x = 5 + a;             # ERROR: left is int, uses built-in int addition
```

## Complete Example

```velo
class Vector(int x, int y) {
    operator +(Vector other) Vector {
        new Vector(x + other.x, y + other.y);
    };

    operator -(Vector other) Vector {
        new Vector(x - other.x, y - other.y);
    };

    operator *(Vector other) Vector {
        new Vector(x * other.x, y * other.y);
    };

    operator /(Vector other) Vector {
        new Vector(x / other.x, y / other.y);
    };

    operator %(Vector other) Vector {
        new Vector(x % other.x, y % other.y);
    };

    operator ==(Vector other) bool {
        x == other.x & y == other.y;
    };

    operator !=(Vector other) bool {
        x != other.x | y != other.y;
    };

    operator <(Vector other) bool {
        x < other.x & y < other.y;
    };

    operator >(Vector other) bool {
        x > other.x & y > other.y;
    };

    operator <=(Vector other) bool {
        x <= other.x & y <= other.y;
    };

    operator >=(Vector other) bool {
        x >= other.x & y >= other.y;
    };

    operator -() Vector {
        new Vector(0 - x, 0 - y);
    };

    operator [](int index) int {
        if (index == 0) then x else y;
    };

    operator []=(int index, int value) void {
        if (index == 0) then x = value else y = value;
    };

    func toString() str {
        "(".con(x.str).con(", ").con(y.str).con(")");
    };
};

Vector a = new Vector(1, 2);
Vector b = new Vector(3, 4);

Vector sum = a + b;           # (4, 6)
Vector diff = a - b;          # (-2, -2)
Vector prod = a * b;          # (3, 8)
Vector quot = new Vector(10, 20) / new Vector(5, 4);  # (2, 5)
Vector rem = new Vector(10, 7) % new Vector(3, 2);    # (1, 1)

bool eq = a == a;             # true
bool neq = a != b;            # true
bool lt = a < b;              # true
bool gt = b > a;              # true
bool le = a <= a;             # true
bool ge = a >= a;             # true

Vector neg = -a;              # (-1, -2)
int first = a[0];             # 1

Vector c = new Vector(0, 0);
c[0] = 10;
c[1] = 20;                   # c is now (10, 20)

# Chained operations
Vector chain = a + b + new Vector(5, 5);  # (9, 11)

# Mixed operator and comparison
bool check = (a + b) == new Vector(4, 6); # true
```

## Operators with Generics

Operator overloading works with generic classes. The standard library `Map[K, V]` uses index operators for key-based access:

```velo
include "lang/map.vel";

Map[str, str] map = new Map[str, str]();
map["name"] = "Velo";        # calls operator []=
ptr[str] val = map["name"];  # calls operator []
```

## How It Works

The compiler transforms operator declarations into internal methods with an `op@` prefix. When the compiler encounters an operator used on a class instance, it dispatches to the corresponding `op@` method:

- `a + b` becomes a call to `a.op@+(b)`
- `-a` becomes a call to `a.op@neg()`
- `a[i]` becomes a call to `a.op@[](i)`
- `a[i] = v` becomes a call to `a.op@[]=(v, i)`

If an operator is used on a class that doesn't define it, the compiler reports an error.

## Limitations

- Operator methods must be declared inside a class body.
- You cannot overload operators on built-in types (`int`, `str`, `bool`, etc.).
- Only the left operand is checked for operator dispatch. There is no reverse/right-hand dispatch.
- Compound assignment operators (`+=`, `-=`, etc.) are not directly overloadable — they desugar to `a = a + b`, so defining `operator +` is sufficient for `+=` to work on reassignable variables.
- Only the operators listed above are allowed. The compiler rejects unsupported operator symbols.

---

[Previous: Generics ←](23-generics.md)
