# Loops

## While Loop

```velo
int i = 1;
while (i <= 5) {
    term.println(i.str());
    i = i + 1;
};
```

An infinite loop is `while (true) { ... }` — exit it with `break` or `return`.

## For Loops

`for` iterates an integer **range** (`start .. end`, end exclusive) or the
elements of an **array**:

```velo
# Range: prints 0 1 2 3 4
for i in 0..5 {
    term.println(i.str());
};

# The bounds are ordinary expressions, evaluated once:
for i in 1..n + 1 {
    fact = fact * i;
};

# Array iteration binds each element in turn:
array[str] names = new array[str]{"ada", "bob", "cid"};
for name in names {
    term.println(name);
};
```

## break and continue

`break` leaves the innermost loop; `continue` skips to the next iteration (in a
`for`, the increment still runs):

```velo
int sum = 0;
for i in 0..100 {
    if (i >= 10) { break; }       # stop once i reaches 10
    if (i % 2 == 0) { continue; } # skip even numbers
    sum = sum + i;
};                                # sum = 1+3+5+7+9 = 25
```

## The loop variable is fresh each iteration

A `for` loop's variable is a **new binding on every iteration**, so a closure
created in the body captures that iteration's value (the same rule as `let` in
JavaScript, or `for` in Kotlin/Swift):

```velo
array[func[int]] handlers = new array[func[int]](3);
for i in 0..3 {
    handlers[i] = func() int { return i; };
};
term.println(handlers[0]().str());   # 0
term.println(handlers[1]().str());   # 1  (not 2!)
term.println(handlers[2]().str());   # 2
```

A `while` loop has no loop variable — its counter is an ordinary variable you
declare outside, so it is **shared** across iterations. To capture per iteration,
bind a fresh local inside the body:

```velo
int j = 0;
while (j < 3) {
    int cur = j;                              # fresh each iteration
    handlers[j] = func() int { return cur; }; # captures 0, 1, 2
    j = j + 1;
};
```

---

[Previous: Conditional Statements ←](06-conditionals.md) | [Next: Functions →](08-functions.md)
