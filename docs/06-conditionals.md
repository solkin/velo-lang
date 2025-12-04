# Conditional Statements

## If-then-else (Expression)

```velo
int a = 5;
str result = if a == 2 then "two" else "not two";
```

## If-then-else (Block)

```velo
int a = 5;
str result = if a == 2 then {
    "two"
} else {
    "not two"
};
```

## Nested Conditionals

```velo
int score = 85;
str grade = if score >= 90 then {
    "A"
} else if score >= 80 then {
    "B"
} else if score >= 70 then {
    "C"
} else {
    "F"
};
```

---

[Previous: Operators ←](05-operators.md) | [Next: Loops →](07-loops.md)

