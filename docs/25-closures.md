# Closures

A **closure** is a function value that carries the variables of the scope it was defined in. When the function is invoked — even far away from where it was written — those captured variables remain reachable.

In Velo every `func` literal is a closure. The captured environment is bound at the moment the function value is created and follows the value wherever it goes.

## Capturing a Value

`makeAdder` returns a function that adds a captured number. Each call to `makeAdder` produces an independent closure with its own `n`:

```velo
func makeAdder(int n) func[int] {
    return func(int x) int {
        return x + n;
    };
};

func[int] add5  = makeAdder(5);
func[int] add10 = makeAdder(10);

add5(3);    # 8
add10(3);   # 13
add5(100);  # 105
```

Note that `add5` and `add10` were created by the same factory; they share neither code nor state.

## Capturing Mutable State

Captured variables are not snapshots — closures read and write the same storage as the surrounding code.

```velo
func makeCounter() func[int] {
    int count = 0;
    return func() int {
        count = count + 1;
        return count;
    };
};

func[int] c1 = makeCounter();
func[int] c2 = makeCounter();

c1();  # 1
c1();  # 2
c2();  # 1 — independent state
c1();  # 3
```

Two counters built from the same factory have separate `count` variables because each invocation of `makeCounter` creates a new local `count`.

## Sharing State Between Closures

A factory can return several closures that close over the same variable. This is the canonical recipe for encapsulated mutable state without a class:

```velo
class Pair(func[void] add, func[int] get) {};

func makeAccumulator() Pair {
    int total = 0;
    return new Pair(
        func(int v) void { total = total + v; },
        func() int { return total; }
    );
};

Pair acc = makeAccumulator();
acc.add(7);
acc.add(35);
acc.get();   # 42
```

## Closures Created in a Loop

A closure created inside a loop captures the **per-iteration** binding of the
loop body's locals — a `for` variable, or any variable declared in the body —
so each closure keeps its own iteration's value, not the final one:

```velo
array[func[int]] fns = new array[func[int]](3);
for i in 0..3 {
    fns[i] = func() int { return i; };
};
fns[0]();  # 0
fns[1]();  # 1  (each closure kept its iteration's i)
fns[2]();  # 2
```

Variables declared **outside** the loop stay shared — closures that mutate them
all see the same one. See [Loops](07-loops.md#the-loop-variable-is-fresh-each-iteration)
for `while` (whose counter is shared) versus `for`.

## Currying

A function that returns another function lets you bind arguments one at a time:

```velo
func add(int a) func[int] {
    return func(int b) int {
        return a + b;
    };
};

func[int] addTen = add(10);
addTen(5);    # 15
add(3)(4);    # 7
```

## Storing Closures in Classes

Closures are ordinary values, so you can keep them in class fields, arrays, dictionaries, or tuples. The captured environment stays alive for as long as the closure itself is reachable.

```velo
class Button(str label, func[void] onClick) {};

int clicks = 0;

Button b = new Button("OK", func() void {
    clicks = clicks + 1;
});

b.onClick();
b.onClick();
b.onClick();
# clicks is now 3
```

The `Button` instance has no idea what `clicks` is — it merely holds the `func[void]` value passed in. Each invocation runs the lambda's body in the scope where it was originally written.

## Lexical, Definition-Site Scoping

Velo closures use **lexical** (sometimes called *definition-site*) scoping. The function does not see the caller's variables; it sees the variables that surrounded it when it was created. This is the same model used by JavaScript, Python, Kotlin, Swift, Scala and most modern languages.

```velo
func make() func[int] {
    int x = 100;
    return func() int { return x; };
};

int x = 1;            # outer x — irrelevant to the closure
func[int] f = make();
f();                   # 100, not 1
```

## Lifetime and Memory

Captured variables live on the JVM heap and are reclaimed by the garbage collector once the closure that captures them becomes unreachable. There is no manual lifetime management: a closure can outlive the function that defined it for as long as you keep a reference to it.

## When To Use Closures

Closures shine wherever you would otherwise wire up a small object with a single method:

- **Callbacks.** Pass a `func[void]` to a constructor or registration function.
- **Stateful generators.** Return a `func[T]` that produces the next value each call.
- **Configuration.** Capture parameters once and reuse the resulting function.
- **Composition.** Build pipelines from `compose`, `twice`, `andThen`, and similar combinators.

For richer behaviour with multiple operations, prefer a class. For one-off callbacks and lightweight state, closures keep the code shorter and the call site simpler.

---

[Previous: Operator Overloading ←](24-operator-overloading.md) | [Next: Actors →](26-actors.md)
