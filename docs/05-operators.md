# Operators

## Arithmetic Operators

```velo
int a = 10;
int b = 3;

int sum = a + b;      # 13
int diff = a - b;     # 7
int prod = a * b;     # 30
int quot = a / b;     # 3
int rem = a % b;      # 1
```

## Unary Operators

```velo
int x = -10;          # Unary minus
int y = -x;           # y = 10
int z = 5 + -3;       # z = 2
int w = -(-2);        # w = 2 (double negation)
```

## Compound Assignment Operators

```velo
int a = 10;

a += 5;               # a = a + 5, result: 15
a -= 3;               # a = a - 3, result: 12
a *= 2;               # a = a * 2, result: 24
a /= 4;               # a = a / 4, result: 6
a %= 4;               # a = a % 4, result: 2
```

Works with array elements and in loops:

```velo
array[int] arr = new array[int] { 1, 2, 3 };
arr[0] += 10;         # arr[0] = 11

int i = 0;
while i < 10 {
    i += 1            # Increment
}
```

## Comparison Operators

```velo
bool eq = a == b;     # Equality
bool ne = a != b;     # Inequality
bool lt = a < b;      # Less than
bool gt = a > b;      # Greater than
bool le = a <= b;     # Less than or equal
bool ge = a >= b;     # Greater than or equal
```

## Logical Operators

**Important:** Velo Lang uses the `&` operator for logical "AND", which **always evaluates both operands** (no short-circuit evaluation like `&&`).

```velo
bool a = true;
bool b = false;

bool and = a & b;     # false (always evaluates both operands)
bool or = a | b;      # true
bool xor = a ^ b;     # true
```

**Negation:** Velo Lang does not have a unary `!` operator. For negation, use comparison with `false`:

```velo
bool value = true;
if (value == false) {
    # Will execute if value == false
};
```

## Bitwise Operators

```velo
int a = 5;
int b = 3;

int shl = a << 1;     # Left shift
int shr = a >> 1;     # Right shift
```

## Operator Overloading

All arithmetic, comparison, unary, and index operators can be overloaded for user-defined classes using the `operator` keyword. See [Operator Overloading](24-operator-overloading.md) for details.

---

[Previous: Variables and Assignment ←](04-variables-and-assignment.md) | [Next: Conditional Statements →](06-conditionals.md)

