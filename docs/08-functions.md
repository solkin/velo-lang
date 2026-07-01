# Functions

## Function Declaration

```velo
func add(int a, int b) int {
    return a + b;
};
```

## Function Calls

```velo
int result = add(5, 3);  # result = 8
```

## Functions Without Parameters

```velo
func getAnswer() int {
    return 42;
};

int answer = getAnswer();
```

## Functions Without Return Value

```velo
func printHello() void {
    term.println("Hello");
};
```

## Lambda Functions

```velo
any add = func(int a, int b) int {
    return a + b;
};

int result = add(5, 3);
```

## Recursion

Velo Lang supports recursive calls:

```velo
func factorial(int n) int {
    return if n <= 1 then 1 else n * factorial(n - 1);
};

func fibonacci(int n) int {
    return if n < 2 then n else fibonacci(n - 1) + fibonacci(n - 2);
};
```

## Function Values and Types

Lambda expressions produce ordinary first-class values. They can be:
- assigned to variables,
- passed as arguments,
- returned from functions,
- stored in fields, arrays, dictionaries, or tuples.

When the full signature is known, use the rich form `func(<args>) <ret>` (typically inferred via `any` for the variable). When only the return type matters — for example, declaring a parameter that accepts any callback — use the loose form `func[<ret>]`:

```velo
# Full signature is preserved when assigned to `any`.
any square = func(int x) int { return x * x; };

# Loose form — only the return type is part of the type.
func[int] callback = square;
int y = callback(7);   # 49
```

> **The loose form `func[T]` is an unchecked escape hatch.** It declares only the
> return type, so calls through such a value are **not** checked for argument count
> or types at compile time — `f(1, 2, 3)` compiles even when `f` actually takes two
> arguments, and the mistake only surfaces at run time. Prefer the full signature
> `func[(<args>) <ret>]` by default; it checks calls and is the only form that may
> cross an actor or native boundary. Reach for `func[T]` only for internal
> higher-order helpers that must accept callbacks of varying arity (e.g. a `map`
> that takes either `(value)` or `(value, index)`).

## Higher-Order Functions

A higher-order function takes a function as a parameter or returns one.

### Functions as Parameters

```velo
func apply(int x, func[int] f) int {
    return f(x);
};

any square = func(int x) int { return x * x; };

int result = apply(5, square);  # 25
```

### Composition

Returning a function from a function lets you build new behaviour by combining existing pieces:

```velo
func compose(func[int] f, func[int] g) func[int] {
    return func(int x) int {
        return f(g(x));
    };
};

any inc    = func(int x) int { return x + 1; };
any square = func(int x) int { return x * x; };

func[int] incThenSquare = compose(square, inc);
int r = incThenSquare(4);   # (4 + 1)^2 = 25
```

### Predicates

```velo
func count(array[int] arr, func[bool] pred) int {
    int n = 0;
    int i = 0;
    while (i < arr.len()) {
        if (pred(arr[i])) {
            n = n + 1;
        };
        i = i + 1;
    };
    return n;
};

int big = count(
    new array[int]{return 3, 12, 7, 25},
    func(int v) bool { return v > 10; }
);  # 2
```

See [Closures](25-closures.md) for how a function value carries the variables of its defining scope along with it.

---

[Previous: Loops ←](07-loops.md) | [Next: Arrays →](09-arrays.md)

