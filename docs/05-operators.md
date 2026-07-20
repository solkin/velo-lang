# Operators

## Arithmetic Operators

```velo
int a = 10
int b = 3

int sum = a + b  # 13
int diff = a - b  # 7
int prod = a * b  # 30
int quot = a / b  # 3
int rem = a % b  # 1
```

## Unary Operators

```velo
int x = -10  # Unary minus
int y = -x  # y = 10
int z = 5 + -3  # z = 2
int w = -(-2)  # w = 2 (double negation)
```

## Compound Assignment Operators

```velo
int a = 10

a += 5  # a = a + 5, result: 15
a -= 3  # a = a - 3, result: 12
a *= 2  # a = a * 2, result: 24
a /= 4  # a = a / 4, result: 6
a %= 4  # a = a % 4, result: 2
```

Works with array elements and in loops:

```velo
array[int] arr = new array[int] { 1, 2, 3 }
arr[0] += 10  # arr[0] = 11

int i = 0
while i < 10 {
    i += 1            # Increment
}
```

## Comparison Operators

```velo
bool eq = a == b  # Equality
bool ne = a != b  # Inequality
bool lt = a < b  # Less than
bool gt = a > b  # Greater than
bool le = a <= b  # Less than or equal
bool ge = a >= b  # Greater than or equal
```

## Logical Operators

Velo has short-circuit logical operators `&&`, `||`, and the unary `!`:

```velo
bool a = true
bool b = false

bool and = a && b  # false (short-circuits: b only evaluated if a is true)
bool or  = a || b  # true  (short-circuits: b only evaluated if a is false)
bool neg = !a  # false
```

`&` and `|` also work on booleans as short-circuit aliases of `&&` / `||`, but
prefer `&&` / `||` for clarity. `^` on two `bool` values is a logical XOR
(returns a `bool`); unlike `&` / `|` it always evaluates both sides.

## Bitwise Operators

`&`, `|`, `^` are bitwise operators on `int` and `long`:

```velo
int a = 5
int b = 3

int band = a & b  # 1  (bitwise AND)
int bor  = a | b  # 7  (bitwise OR)
int bxor = a ^ b  # 6  (bitwise XOR)
```

There are **no shift operators** (`<<` / `>>`). Shift with the `.shl(n)` /
`.shr(n)` / `.ushr(n)` methods instead:

```velo
int shl  = a.shl(1)  # 10 (left shift by 1)
int shr  = a.shr(1)  # 2  (arithmetic right shift, sign-propagating)
int ushr = a.ushr(1)  # 2  (logical right shift, zero-fill)
```

## Operator Overloading

All arithmetic, comparison, unary, and index operators can be overloaded for user-defined classes using the `operator` keyword. See [Operator Overloading](24-operator-overloading.md) for details.

---

[Previous: Variables and Assignment ←](04-variables-and-assignment.md) | [Next: Conditional Statements →](06-conditionals.md)

