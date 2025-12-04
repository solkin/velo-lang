# Tuples

## Creating Tuples

```velo
tuple[int, str] pair = new tuple(1, "second");
tuple[int, str, float] triple = new tuple(42, "text", 3.14);
```

## Accessing Elements

```velo
tuple[int, str] p = new tuple(1, "second");
int first = p.1;         # 1
str second = p.2;         # "second"
```

## Modifying Elements

Tuples in Velo Lang are mutable:

```velo
tuple[int, str] p = new tuple(1, "hello");
p.1 = 42;                # Change first element
p.2 = "world";           # Change second element
```

---

[Previous: Strings ←](11-strings.md) | [Next: Classes →](13-classes.md)

