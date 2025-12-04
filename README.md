# Velo Lang

**Simple. Embeddable. Yours.**

Velo Lang is a functional, strict-typed compilable programming language. It runs on top of lightweight stack virtual machine.

![Velo Lang Logo](/velo-logo.png)

## Features

- ✅ **Strict Typing** - All variables have explicit types
- ✅ **Functional Style** - Support for higher-order functions and lambdas
- ✅ **Compilable** - Code compiles to bytecode for virtual machine
- ✅ **Embeddable** - Easy integration into other applications
- ✅ **Native Classes** - Support for binding to native code via reflection
- ✅ **Standard Library** - Built-in support for HTTP, file system, terminal I/O, and more

## Quick Start

### Hello, World!

```velo
include "lang/terminal.vel";

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
byte c = 2y;
float f = 3.0;
array[int] arr = new array[int]{1, 2, 3};
dict[int:str] d = new dict[int:str]{1:"a", 2:"b"};
tuple[int,str] p = new tuple(1, "second");
any value = 42;          # Universal type
```

### Functions

```velo
func add(int a, int b) int {
    a + b;
};

# Lambda
any multiply = func(int a, int b) int {
    a * b;
};

# Recursive
func fib(int n) int {
    if n < 2 then n else fib(n - 1) + fib(n - 2);
};
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
    term.println(i.str);
    i = i + 1;
};
```

### Arrays

```velo
array[int] numbers = new array[int]{37, 58, 25, 17, 19};
println(numbers.len);           # 5
println(numbers[3]);            # 17
println(numbers.sub(1, 4)[1]);  # 25

array[int] doubled = numbers.map(
    func(int i, int v) int {
        v * 2
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
d.set(5, "e");
println(d.del(2));        # true
println(d.len);           # 3
println(d[5]);            # e
println(d.key(5));        # true
println(d.keys[0]);       # first key
```

### Strings

```velo
str s = "Test String";
println(s.len);        # 11
println(s.sub(5, 11)); # String
str combined = "Hello".con(", ").con("World");
```

### Tuples

```velo
tuple[int,str] p = new tuple(1, "second");
println(p.1.str);  # 1
println(p.2);      # second
p.1 = 42;          # Mutating tuple
```

### Classes

```velo
class Random(int seed) {
    int a = 252149039;
    int c = 11;
    int previous = 0;

    func setSeed(int seed) void {
        previous = seed;  # Изменение поля внутри метода
    };

    func next() int {
        int r = a * previous + c;
        previous = r;     # Изменение поля внутри метода
        r;
    }
};

Random random = new Random(12345);
int value = random.next();
# random.previous = 10;  # ОШИБКА: поля доступны только для чтения снаружи
```

### Extension Functions

```velo
ext(int a) max(int b) int {
    if (a > b) then a else b;
};

ext(str a) insert(int index, str s) str {
    a.sub(0, index).con(s).con(a.sub(index, a.len));
};

int maxValue = 5.max(10);  # 10
str result = "Hello".insert(5, " World");  # "Hello World"
```

### Native Classes

Velo Lang supports native classes binding via reflection:

```velo
native class Terminal() {
    native func print(str text) str;
    native func input() str;
    func println(str text) str {
        print(text.con("\n"));
    };
};

Terminal term = new Terminal();
term.println("Hello, World!");
```

## Standard Library

### Terminal I/O

```velo
include "lang/terminal.vel";

Terminal term = new Terminal();
term.print("Enter your name: ");
str name = term.input();
term.println("Hello, ".con(name));
```

### Time Operations

```velo
include "lang/time.vel";

Time time = new Time();
time.sleep(1000);          # Sleep for 1 second
int unixTime = time.unix(); # Unix timestamp
```

### HTTP Requests

```velo
include "lang/http.vel";

Http http = new Http();
str response = http.get("https://api.example.com/data");
int status = http.statusCode();

# POST request
str jsonBody = "{\"key\": \"value\"}";
str postResponse = http.post("https://api.example.com/endpoint", jsonBody, "");
```

### File System

```velo
include "lang/filesystem.vel";

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

### Important Limitations

- **No unary `!` operator** - Use `== false` for negation
- **No `&&` operator** - Use `&` which always evaluates both operands
- **No `||` operator** - Use `|`
- **No `break` or `continue`** - Use conditional statements
- **No method overloading** - Each method name must be unique
- **Class fields are read-only from outside** - Fields can only be modified inside class methods

### Boolean Operations

```velo
# Negation
if (value == false) {
    # Execute if value is false
};

# Logical AND (always evaluates both operands)
if (a & b) {
    # Both a and b are evaluated
};

# Logical OR
if (a | b) {
    # At least one is true
};
```

## Examples

The project includes many example programs in `src/main/resources/`:

- `hello.vel` - Hello, World program
- `fibonacci-recursive.vel` - Recursive Fibonacci algorithm
- `primes-range.vel` - Find prime numbers in a range
- `lzw.vel` - LZW compression algorithm
- `huffman.vel` - Huffman compression algorithm
- `class.vel` - Class usage examples
- `ext.vel` - Extension function examples
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

# Run a program
./gradlew run --args="hello.vel"
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
