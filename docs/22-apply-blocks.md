# Apply Blocks (Context Blocks)

## Overview

Apply blocks allow executing operations in the context of an expression's result. This is analogous to `.apply{}` in Kotlin, but with a more concise syntax — just curly braces `{}` after any expression.

## Syntax

```velo
<expression> {
    # inside the block, a special variable `it` is available
    # which references the result of the expression on the left
}
```

## Basic Examples

### Object Initialization

```velo
class Person(str name, int age) {
    func setName(str n) void { name = n; };
    func setAge(int a) void { age = a; };
};

# Without apply block:
Person p = new Person("", 0);
p.setName("John");
p.setAge(25);

# With apply block:
Person p = new Person("", 0) {
    it.setName("John");
    it.setAge(25);
};
```

### Call Chaining

```velo
str result = "hello" {
    it = it + " world";
    it = it + "!";
};
# result = "hello world!"
```

### Working with Arrays

```velo
array[int] arr = new array[int](5) {
    it[0] = 1;
    it[1] = 2;
    it[2] = 3;
    it[3] = 4;
    it[4] = 5;
};
```

### Nested Apply Blocks

```velo
class Point(int x, int y) {
    func setX(int val) void { x = val; };
    func setY(int val) void { y = val; };
};

class Line(Point start, Point end) {};

Line line = new Line(new Point(0, 0), new Point(0, 0)) {
    it.start {
        it.setX(10);
        it.setY(20);
    };
    it.end {
        it.setX(100);
        it.setY(200);
    };
};
```

## Semantics

1. **Context variable `it`**: Inside an apply block, a special variable `it` is available, containing a reference to the result of the expression to the left of `{}`.

2. **Return value**: An apply block always returns the value of `it` (even if the last expression in the block returns something else).

3. **Type**: The type of an apply block matches the type of the expression on the left.

4. **Scope**: The variable `it` is only available inside the apply block and shadows any outer variables with the same name.

## Advanced Examples

### Modification and Return

```velo
# Creation and initialization in a single expression
func createConfiguredPerson() Person {
    new Person("", 0) {
        it.setName("Default");
        it.setAge(18);
    };
};
```

### Working with Dictionaries

```velo
dict[str:int] scores = new dict[str:int]{} {
    it["Alice"] = 100;
    it["Bob"] = 95;
    it["Charlie"] = 87;
};
```

### Conditional Initialization

```velo
Person p = new Person("", 0) {
    if (needsDefault) {
        it.setName("Anonymous");
    } else {
        it.setName(userName);
    };
    it.setAge(calculateAge());
};
```

## Edge Cases

### Apply Block with Primitives

```velo
int x = 5 {
    it = it * 2;  # it = 10
};
# x = 10
```

### Empty Apply Block

```velo
Person p = new Person("John", 25) {};
# Equivalent to: Person p = new Person("John", 25);
```

### Chained Apply Blocks

```velo
Person p = new Person("", 0) {
    it.setName("John");
} {
    it.setAge(25);
};
```

### Apply Block in Expression

```velo
int total = (new Counter() { it.add(5); }).value + 10;
```

---

[Previous: Pointers ←](21-pointers.md)
