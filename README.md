# Velo Lang

**Simple. Embeddable. Yours.**

Velo Lang is a functional, strict-typed compilable programming language. It runs on top of lightweight stack virtual machine.

![Velo Lang Logo](/velo-logo.png)

## Features

- ✅ **Strict Typing** - All variables have explicit types
- ✅ **Functional Style** - Higher-order functions, lambdas and lexical closures
- ✅ **Compilable** - Code compiles to bytecode for virtual machine
- ✅ **Embeddable** - Easy integration into other applications
- ✅ **Generics** - Type-safe generic classes, functions, and methods
- ✅ **Operator Overloading** - Custom operators for user-defined classes
- ✅ **Native Classes** - Host classes bound by registration, with two-way callbacks
- ✅ **Actors** - `actor class` + `await` for thread-isolated state without locks
- ✅ **Standard Library** - Built-in support for HTTP, file system, terminal I/O, and more

## Quick Start

### Hello, World!

```velo
Terminal term = new Terminal();

str hello = "Hello, World!";
term.println(hello);
```

### Run a Program

```bash
./gradlew run --args="/path/to/program.vel"
```

## Language Overview

### Data Types

```velo
str s = "s";
bool b = true;
int i = 1;
int hex = 0xCAFE;        # Hexadecimal
int binary = 0b101010;    # Binary
byte c = 2;              # int literal in byte range
float f = 3.0;
array[int] arr = new array[int]{1, 2, 3};
dict[int:str] d = new dict[int:str]{1:"a", 2:"b"};
tuple[int,str] p = new tuple(1, "second");
any value = 42;          # Universal type
```

### Functions

```velo
func add(int a, int b) int {
    return a + b;
};

# Lambda
any multiply = func(int a, int b) int {
    return a * b;
};

# Recursive
func fib(int n) int {
    return if n < 2 then n else fib(n - 1) + fib(n - 2);
};
```

### Higher-Order Functions and Closures

Functions are first-class values: pass them around, return them, store them.
Lambdas capture the variables of the scope they were defined in (lexical closures).

```velo
# Higher-order: a function that takes a function
func apply(int x, func[int] f) int {
    return f(x);
};

# Closure: returned lambda remembers `n` after makeAdder returns
func makeAdder(int n) func[int] {
    return func(int x) int { return x + n; };
};

func[int] add5 = makeAdder(5);
int r = apply(3, add5);   # 8
```

### Conditional Operators

```velo
int a = 5;
str result = if a == 2 then "two" else "not two";

# Block form
str grade = if score >= 90 then {
    "A"
} else if score >= 80 then {
    "B"
} else {
    "C"
};
```

### Loops

```velo
int i = 1;
while (i <= 5) {
    term.println(i.str());
    i = i + 1;
};
```

### Arrays

```velo
array[int] numbers = new array[int]{37, 58, 25, 17, 19};
term.println(numbers.len().str());        # 5
term.println(numbers[3].str());           # 17
term.println(numbers.sub(1, 4)[1].str()); # 25

# map callback is value-first, index optional
array[int] doubled = numbers.map(
    func(int v) int {
        return v * 2
    }
);
```

### Dictionaries

```velo
dict[int:str] d = new dict[int:str]{
    1:"a",
    2:"b",
    3:"c"
};
d.put(5, "e");
term.println(d.del(2).str());     # true
term.println(d.len.str());        # 3  (len is a field → bare)
term.println(d[5]);               # e
term.println(d.key(5).str());     # true
term.println(d.keys()[0].str());  # first key
```

### Strings

```velo
str s = "Test String";
term.println(s.len().str()); # 11
term.println(s.sub(5, 11));  # String
str combined = "Hello".con(", ").con("World");
```

### Tuples

```velo
tuple[int,str] p = new tuple(1, "second");
term.println(p.1.str());  # 1
term.println(p.2);        # second
p.1 = 42;                 # Mutating tuple
```

### Classes

```velo
class Random(int seed) {
    int a = 252149039;
    int c = 11;
    int previous = 0;

    func setSeed(int seed) void {
        previous = seed;
    };

    func next() int {
        int r = a * previous + c;
        previous = r;
        return r;
    }
};

Random random = new Random(12345);
int value = random.next();
# random.previous = 10;  # ERROR: fields are read-only from outside
```

### Operator Overloading

```velo
class Vector(int x, int y) {
    operator +(Vector other) Vector {
        return new Vector(x + other.x, y + other.y);
    };

    operator ==(Vector other) bool {
        return x == other.x && y == other.y;
    };

    operator [](int index) int {
        return if (index == 0) then x else y;
    };
};

Vector a = new Vector(1, 2);
Vector b = new Vector(3, 4);
Vector sum = a + b;          # Vector(4, 6)
bool eq = a == a;            # true
int first = a[0];            # 1
```

### Extension Functions

```velo
ext(int a) max(int b) int {
    return if (a > b) then a else b;
};

ext(str a) insert(int index, str s) str {
    return a.sub(0, index).con(s).con(a.sub(index, a.len()));
};

int maxValue = 5.max(10);  # 10
str result = "Hello".insert(5, " World");  # "Hello World"
```

### Actors

Concurrency without locks: an `actor class` instance has isolated private state, and every interaction crosses the boundary via `async` (start) + `await` (wait). Actors run cooperatively on a single event loop by default; a host can plug in a thread backend for real multicore parallelism.

```velo
actor class Counter(int start) {
    int n = start;
    func bump() int {
        n += 1;
        return n;
    };
};

actor[Counter] c = new Counter(0);
term.println((await async c.bump()).str());  # 1
term.println((await async c.bump()).str());  # 2

# Overlap calls: start both, await later (parallel with a thread backend).
actor[Counter] d = new Counter(100);
future[int] f1 = async c.bump();
future[int] f2 = async d.bump();
int x = await f1;   # both calls in flight before we await
int y = await f2;
```

See [Actors](docs/26-actors.md) for the full model — argument cloning, identity preservation, lifetime management, parallel work patterns.

### Native Classes

Host (JVM) classes are bound by registration — no declarations in Velo
source. Register a plain Kotlin/Java class on the runtime and its Velo type
is synthesized from the class itself; signatures are checked at compile
time and linked before the program runs:

```kotlin
val runtime = VeloRuntime().register(Terminal::class)
```

```velo
Terminal term = new Terminal();
term.println("Hello, World!");
```

Two-way integration: a `func[(args) void]` argument arrives in native code
as a `VeloFunction` (or a plain Kotlin `(Int) -> Unit`), and invoking it
from any thread runs the closure back on its owning Velo thread — see
[Callbacks](docs/27-callbacks.md).

## Standard Library

### Terminal I/O

```velo
Terminal term = new Terminal();
term.print("Enter your name: ");
str name = term.input();
term.println("Hello, ".con(name));
```

### Time Operations

```velo
Time time = new Time();
time.sleep(1000);          # Sleep for 1 second
int unixTime = time.unix(); # Unix timestamp
```

### HTTP Requests

```velo
Http http = new Http();
str response = http.get("https://api.example.com/data");
int status = http.statusCode();

# POST request
str jsonBody = "{\"key\": \"value\"}";
str postResponse = http.post("https://api.example.com/endpoint", jsonBody, "");
```

### File System

```velo
FileSystem fs = new FileSystem();
fs.write("file.txt", "Content");
str content = fs.read("file.txt");
fs.append("file.txt", "\nMore content");

bool exists = fs.exists("file.txt");
array[str] files = fs.list(".");
fs.copy("source.txt", "dest.txt");
fs.delete("file.txt");
```

## Language Notes

### Things to know

- **Logical operators** `&&`, `||`, `!` short-circuit; `&`, `|`, `^` are bitwise
  on `int`/`long` (and `&`/`|` alias `&&`/`||` on booleans).
- **No shift operators** — use `x.shl(n)` / `x.shr(n)`.
- **`return` is mandatory** — a non-`void` function must `return` on every path;
  there is no implicit last-expression return.
- **Method and conversion calls need `()`** — `x.str()`, `arr.len()`; bare access
  is only for stored fields (`obj.field`, `tuple.1`, `map.len`).
- **`break` / `continue`** work in `while`, `for`-range and `for`-each loops.
- **No method overloading** — each method name must be unique.
- **Class fields are read-only from outside** — fields can only be modified
  inside class methods.

### Boolean Operations

```velo
# Negation
bool ready = !done;

# Logical AND / OR (short-circuit)
if (a && b) {
    # b only evaluated if a is true
};
if (a || b) {
    # b only evaluated if a is false
};
```

## Project Structure

The project is split into focused Gradle modules so the compiler and the VM
can be used independently:

- `velo-core` — the contract shared by both sides: the `Op` instruction set,
  `VmType`, the native-interop registry/descriptors, and the `.vbc` bytecode
  format (`Bytecode`). No execution engine.
- `velo-compiler` — parser and compiler: `.vel` sources → `SerializedProgram`.
  Depends only on `velo-core`; a build tool can compile bytecode without the VM.
- `velo-vm` — the execution engine: interpreter, records, memory, actors, and
  the embedding API (`VeloRuntime`). Depends only on `velo-core`; a client
  application can run `.vbc` programs without the compiler.
- `velo-vm2` — a clean-room reimplementation of the VM written from the `.vbc`
  spec and verified against the golden tests (a parity gate for the format).
- `velo-vm3` — a second clean-room, performance-oriented VM, also verified
  against the corpus (a second parity gate alongside `velo-vm2`).
- `velo-cli` — the command-line tool plus the default native classes
  (`Terminal`, `Time`, `FileSystem`, `Http`, `Socket`). The only module that
  links the compiler and the VM together.

Embedding the VM in an application:

```kotlin
val runtime = VeloRuntime().register(MyApi::class)
runtime.run(Bytecode.read(File("app.vbc")))
```

Compiling without running:

```kotlin
val compiler = VeloCompiler().register(MyApi::class)
val program = compiler.compile("app.vel") ?: return
Bytecode.write(program, File("app.vbc"))
```

## Examples

The project includes many example programs in `velo-cli/src/main/resources/`:

- `hello.vel` - Hello, World program
- `fibonacci-recursive.vel` - Recursive Fibonacci algorithm
- `primes-range.vel` - Find prime numbers in a range
- `lzw.vel` - LZW compression algorithm
- `huffman.vel` - Huffman compression algorithm
- `class.vel` - Class usage examples
- `ext.vel` - Extension function examples
- `interfaces.vel` - Structural interfaces, `Self`, bounded generics and native conformance
- `closures.vel` - Closures, captured state and currying
- `higher-order.vel` - `apply`, `compose`, `count` and array.map with captures
- `http-example.vel` - HTTP request examples
- `filesystem-example.vel` - File system operations examples
- `game-of-life.vel` - Conway's Game of Life implementation

## Running Programs

### From File

Run a program from a file:

```bash
./gradlew run --args="/path/to/program.vel"
```

### From Bytecode

Run sample programs from bytecode:

```bash
./gradlew run --args="/path/to/bytecode.vbc"
```

## Building

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run a program (paths resolve from the repository root)
./gradlew run --args="velo-cli/src/main/resources/hello.vel"
```

## Documentation

For complete documentation, see [docs/README.md](docs/README.md).

The documentation includes:
- Complete language reference
- All data types and operators
- Standard library reference
- Best practices
- Examples and tutorials

## License

MIT License

Copyright (c) 2025 Igor Solkin

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
