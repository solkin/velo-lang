# Pointers

Velo Lang supports safe pointers that allow pass-by-reference semantics and indirect value modification.

## Overview

Pointers in Velo Lang are:
- **Type-safe** - A `ptr[int]` can only point to `int` values
- **Null-safe** - Null pointers can be explicitly checked
- **Memory-safe** - No pointer arithmetic, automatic memory management

## Creating Pointers

### With Initial Value

```velo
ptr[int] p = new ptr[int](42);
```

### Null Pointer

```velo
ptr[int] nullPtr = new ptr[int];
# or with empty parentheses
ptr[int] nullPtr2 = new ptr[int]();
# or using null literal
ptr[int] nullPtr3 = null;
```

### Assigning Null

You can assign `null` to any pointer to nullify it:

```velo
include "lang/bool.vel";

ptr[int] p = new ptr[int](42);
term.println(p.val.str);  # 42

p = null;                  # nullify the pointer
term.println((p == null).str); # true
```

## Dereferencing

Use `.val` or `.*` property to read or write through a pointer:

```velo
ptr[int] p = new ptr[int](42);

# Reading
int value = p.val;    # value = 42
int value2 = p.*;     # alternative syntax

# Writing
p.val = 100;
p.* = 200;            # alternative syntax
```

### Using `*` Operator

You can also use the prefix `*` operator:

```velo
ptr[int] p = new ptr[int](42);

int value = *p;       # read
*p = 100;             # write
```

## Address-of Operator

Use `&` to create a pointer to an existing variable:

```velo
int x = 10;
ptr[int] px = &x;

px.val = 20;
# x is now 20
```

### Pointer to Array Element

```velo
array[int] arr = new array[int]{10, 20, 30};
ptr[int] p = &arr[1];

p.val = 999;
# arr is now {10, 999, 30}
```

## Null Checking

Use `== null` or `!= null` to check if a pointer is null:

```velo
include "lang/bool.vel";

ptr[int] p = new ptr[int];

if (p == null) {
    term.println("Pointer is null");
} else {
    term.println("Value: ".con(p.val.str));
};

# Using bool.str extension
term.println("Is null: ".con((p == null).str));
term.println("Not null: ".con((p != null).str));
```

## Common Use Cases

### Swap Function

```velo
func swap(ptr[int] a, ptr[int] b) void {
    int tmp = a.val;
    a.val = b.val;
    b.val = tmp;
};

int x = 10;
int y = 20;
swap(&x, &y);
# x = 20, y = 10
```

### Output Parameters

```velo
func divmod(int a, int b, ptr[int] quotient, ptr[int] remainder) void {
    quotient.val = a / b;
    remainder.val = a % b;
};

int q = 0;
int r = 0;
divmod(17, 5, &q, &r);
# q = 3, r = 2
```

### Increment Function

```velo
func increment(ptr[int] counter) void {
    counter.val = counter.val + 1;
};

int count = 0;
increment(&count);  # count = 1
increment(&count);  # count = 2
```

### Modify Multiple Values

```velo
func addToAll(ptr[int] a, ptr[int] b, ptr[int] c, int delta) void {
    a.val = a.val + delta;
    b.val = b.val + delta;
    c.val = c.val + delta;
};

int v1 = 1;
int v2 = 2;
int v3 = 3;
addToAll(&v1, &v2, &v3, 10);
# v1 = 11, v2 = 12, v3 = 13
```

## Pointers in Classes

```velo
class SharedCounter() {
    ptr[int] valuePtr = new ptr[int](0);
    
    func increment() void {
        valuePtr.val = valuePtr.val + 1;
    };
    
    func getValue() int {
        valuePtr.val;
    };
};

SharedCounter c = new SharedCounter();
c.increment();
c.increment();
int value = c.getValue();  # 2
```

## Pointer Types

Pointers can reference any type:

```velo
# Primitive types
ptr[int] pi = new ptr[int](42);
ptr[float] pf = new ptr[float](3.14);
ptr[str] ps = new ptr[str]("hello");
ptr[bool] pb = new ptr[bool](true);

# Arrays
ptr[array[int]] parr = new ptr[array[int]](new array[int]{1, 2, 3});

# Classes
Point point = new Point(10, 20);
ptr[Point] pp = &point;
```

## Restrictions

1. **No Pointer Arithmetic** - You cannot add/subtract from pointers
2. **No Null Dereference** - Dereferencing a null pointer throws an exception
3. **Single-level Only** - No pointers to pointers (`ptr[ptr[int]]` is not allowed)

## Best Practices

1. **Check for null** before dereferencing if pointer may be null:
   ```velo
   if (p != null) {
       int value = p.val;
   };
   ```

2. **Prefer address-of** (`&`) for existing variables instead of creating new boxes

3. **Use for output parameters** when function needs to return multiple values

4. **Document ownership** - make it clear who "owns" the pointed-to data

---

[Previous: Best Practices ←](20-best-practices.md) | [Next: Apply Blocks →](22-apply-blocks.md)

