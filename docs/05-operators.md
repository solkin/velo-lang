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

---

[Previous: Variables and Assignment ←](04-variables-and-assignment.md) | [Next: Conditional Statements →](06-conditionals.md)

