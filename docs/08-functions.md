# Functions

## Function Declaration

```velo
func add(int a, int b) int {
    a + b;
};
```

## Function Calls

```velo
int result = add(5, 3);  # result = 8
```

## Functions Without Parameters

```velo
func getAnswer() int {
    42;
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
    a + b;
};

int result = add(5, 3);
```

## Recursion

Velo Lang supports recursive calls:

```velo
func factorial(int n) int {
    if n <= 1 then 1 else n * factorial(n - 1);
};

func fibonacci(int n) int {
    if n < 2 then n else fibonacci(n - 1) + fibonacci(n - 2);
};
```

## Higher-Order Functions

Functions can accept other functions as parameters:

```velo
func apply(int x, func[int] f) int {
    f(x);
};

any square = func(int x) int {
    x * x;
};

int result = apply(5, square);  # 25
```

---

[Previous: Loops ←](07-loops.md) | [Next: Arrays →](09-arrays.md)

