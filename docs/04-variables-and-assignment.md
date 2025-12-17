# Variables and Assignment

## Variable Declaration

```velo
# With explicit type
int x = 10;
str name = "Velo";

# With any type
any value = 42;
value = "Hello";
```

## Assignment

```velo
int a = 5;
a = 10;              # Change value
a = a + 1;           # Arithmetic operations
```

## Compound Assignment

Velo supports compound assignment operators for concise updates:

```velo
int a = 10;

a += 5;              # Same as: a = a + 5
a -= 3;              # Same as: a = a - 3
a *= 2;              # Same as: a = a * 2
a /= 4;              # Same as: a = a / 4
a %= 3;              # Same as: a = a % 3
```

---

[Previous: Data Types ←](03-data-types.md) | [Next: Operators →](05-operators.md)

