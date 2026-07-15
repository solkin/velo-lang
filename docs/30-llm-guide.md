# LLM Guide — Velo in One Page

A dense cheat-sheet for generating **correct** Velo. Velo looks like C/Java/Kotlin
but has a handful of deliberate rules that trip up code models. Read the seven
rules first — most mistakes come from breaking one of them.

## The seven rules that differ from other languages

1. **A newline ends a statement** (Go-style). No `;` is needed at end of line, and
   **never** write `};`. Use `;` only to put two statements on one line. A statement
   may span lines if it breaks *after* an operator, a `.`, a comma, or an open bracket.
2. **`return` is mandatory and explicit.** There is no "last expression is the
   result". A non-`void` function must `return` on **every** path (both branches of a
   terminal `if` must return, or add a trailing `return`).
3. **Parentheses = computation.** Any method, conversion, or extension call is written
   with `()` — *even with no arguments*: `x.str()`, `arr.len()`, `list.keys()`. Bare
   access (`obj.field`, `tuple.1`, `map.len`) is **only** for stored fields. Calling
   without `()` or reading a field with `()` is a compile error.
4. **Types are explicit** on every declaration and signature. The one exception:
   `let name = expr` declares an **immutable, type-inferred** local.
5. **Logical vs bitwise are separate.** `&&` `||` `!` are logical (short-circuit);
   `&` `|` `^` are bitwise on ints. Negate a bool with `!x`.
6. **String interpolation** is Kotlin-style: `"$name ${expr}"`. Write `\$` for a
   literal dollar sign. Convert values with `.str()` when concatenating with `+`.
7. **Declare before use.** The program runs top-to-bottom; a function must be defined
   above the code that calls it.
8. **Numbers widen implicitly, narrow explicitly.** `byte`→`int`→`long`→`float` is
   automatic (`float f = 5` holds `5.0`, so `f / 2` is `2.5`). Going the other way loses
   data and is a compile error — convert with `.byte()` / `.int()` / `.long()` / `.float()`
   (float→int truncates). No literal suffixes: an int literal takes its target's type (a
   decimal literal over 32 bits is a `long`); a `.` makes a literal a float.

## Syntax at a glance

```velo
# Comment. Native objects are constructed with `new`.
Terminal term = new Terminal()

# Import a module — `.vel` optional; `std/…` is the standard library,
# any other path is relative to this file.
import "std/bool"
import "geom" as g               # namespaced: reach members as g.area(), new g.Point()

# Immutable inferred local (preferred for locals):
let x = 42                       # int, cannot be reassigned
let name = "Velo"                # str

# Explicit typed (mutable) declaration:
int count = 0
count = count + 1                # ok, mutable

# Functions: explicit param types and return type, explicit return.
func add(int a, int b) int {
    return a + b
}

func sign(int n) int {           # every path returns
    if (n > 0) {
        return 1
    }
    if (n < 0) {
        return -1
    }
    return 0
}

# `if` is also an expression with then/else (parens around the condition optional):
let label = if x > 0 then "positive" else "non-positive"

# Loops: while, for-range (end exclusive), for-each. break / continue work anywhere.
int sum = 0
for i in 0..5 {                  # 0,1,2,3,4
    if (i == 3) { continue }
    sum += i
}

int j = 0
while (j < 10) {
    if (j > 4) { break }
    j += 1
}

# Strings: interpolation and .str() conversions (int/float/bool/str all convert).
let pi = 3.14
let ok = true
term.println("x=$x pi=$pi ok=$ok sum=${sum}")
term.println("count = " + count.str())     # + needs .str() on non-strings

# Arrays: sized vs literal; .len(); map takes value first, index optional.
array[int] a = new array[int](3)            # length 3 (NOT zero-initialised)
array[int] b = new array[int]{10, 20, 30}   # literal
term.println(b.len().str())                 # method → ()
array[int] doubled = b.map(func(int v) int { return v * 2 })
array[int] withIdx = b.map(func(int v, int i) int { return v + i })

# Dictionaries (sugar over the Map class; one type, one API):
dict[str:int] ages = new dict[str:int]{ "ada": 36, "bob": 40 }
ages["cid"] = 25                # index set
let a1 = ages["ada"]            # index get → the value
term.println(ages.len.str())    # len is a FIELD → bare, no ()
term.println(ages.key("bob").str())   # key(...) is a METHOD → ()

# Tuples: positional fields are bare and 1-indexed.
tuple[int, str] pair = new tuple(1, "one")
term.println(pair.1.str())      # 1
term.println(pair.2)            # "one"
```

## Errors: try / catch / throw

```velo
# Using try/throw auto-imports std/error — no `import` needed.
try {
    int x = 1 / 0                        # a runtime failure...
} catch (Error e) {                      # caught type is always Error
    term.println("${e.kind}: ${e.message}")   # e.kind, e.message are str
}

throw new Error("timeout", "too slow")   # raise your own: kind + message
throw "quick"                            # shorthand → new Error(ERR_GENERIC, "quick")
```

- `try`/`catch` is a **statement** (no value, unlike `if`); the catch variable is
  visible only inside its block.
- `e.kind` is a machine-readable category — compare to an `ERR_*` constant, don't
  hard-code the literal. Built-ins: `ERR_ARITHMETIC`, `ERR_BOUNDS`, `ERR_NULL`,
  `ERR_NATIVE`, `ERR_ACTOR`, `ERR_GENERIC`. Name your own the same way (`let ERR_X = "x"`).
- A `try` around an `await` catches an actor failure (`e.kind == ERR_ACTOR`).
- An uncaught error is fatal; `throw e` inside a `catch` re-raises. `halt` is never catchable.

## Classes, data classes, interfaces

```velo
class Point(int x, int y) {
    int mag = x * x + y * y      # field initialised in the constructor body
    func dist2() int {           # method: called as p.dist2()
        return mag
    }
    func moved(int dx, int dy) Point {
        return new Point(x + dx, y + dy)
    }
}

Point p = new Point(3, 4)
term.println(p.mag.str())        # field → bare
term.println(p.dist2().str())    # method → ()

# data class: immutable value type, compared by value, transferable to actors.
data class Vec(int x, int y) {
    func add(Vec o) Vec { return new Vec(x + o.x, y + o.y) }
}

# interface: satisfied structurally (no `extends`).
interface Shape { func area() int; }
class Square(int side) {
    func area() int { return side * side }
}
Shape s = new Square(5)
term.println(s.area().str())
```

## Concurrency (actors, async/await, callbacks)

```velo
actor class Counter() {
    int n = 0
    func bump() int {
        n += 1
        return n
    }
}
actor[Counter] c = new Counter()
let v = await async c.bump()     # async → future[int], await unwraps it
term.println(v.str())
```

## Common mistakes (❌ wrong → ✅ right)

| ❌ Wrong | ✅ Right | Why |
|---------|---------|-----|
| `while (i<10) { ... };` | `while (i < 10) { ... }` | no `;` after `}` |
| `func f() int { a + b }` | `func f() int { return a + b }` | explicit return required |
| `x.str` , `arr.len` | `x.str()` , `arr.len()` | conversions/methods need `()` |
| `map.len()` | `map.len` | `len` is a stored field → bare |
| `dict.get(k).str()` | `dict[k].str()` | index returns the value; `get` returns a `ptr` |
| `"total " + n` | `"total " + n.str()` or `"total $n"` | convert / interpolate |
| `var x = 5` | `let x = 5` or `int x = 5` | no `var`; `let` = immutable inferred |
| `list.map(func(int i, int v){...})` | `list.map(func(int v){...})` | callback is value-first, index optional |
| calling a func defined below | move the definition above the call | declare-before-use |

## Complete example — FizzBuzz

```velo
Terminal term = new Terminal()

func label(int n) str {
    if (n % 15 == 0) { return "FizzBuzz" }
    if (n % 3 == 0)  { return "Fizz" }
    if (n % 5 == 0)  { return "Buzz" }
    return n.str()
}

for n in 1..16 {
    term.println(label(n))
}
```

## Complete example — words counted in a dict

```velo
Terminal term = new Terminal()

array[str] words = new array[str]{"a", "b", "a", "c", "b", "a"}
dict[str:int] counts = new dict[str:int]{}

for w in words {
    if (counts.key(w)) {
        counts[w] = counts[w] + 1
    } else {
        counts[w] = 1
    }
}

array[str] keys = counts.keys()
for k in keys {
    term.println("$k: ${counts[k]}")
}
```

---

[Previous: Interfaces ←](29-interfaces.md) | [Next: Grammar →](31-grammar.md)
