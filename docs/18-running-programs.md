# Running Programs

## Running from File

Pass the path to a `.vel` file (paths resolve from the repository root):

```bash
./gradlew run --args="velo-cli/src/main/resources/hello.vel"
./gradlew run --args="velo-cli/src/main/resources/primes-range.vel"
```

`import` paths are resolved relative to the importing file; standard-library
modules (`import "std/map"`) are bundled with the compiler, so they need no
files next to your program.

## Compiling to Bytecode

A second argument compiles the program to a `.vbc` bytecode file instead of
running it; a `.vbc` file as the first argument runs pre-compiled bytecode:

```bash
./gradlew run --args="velo-cli/src/main/resources/hello.vel hello.vbc"
./gradlew run --args="hello.vbc"
```

## Example Programs

The project includes many examples in `velo-cli/src/main/resources/`:

- `hello.vel` — Hello, World
- `fibonacci-recursive.vel` — Recursive Fibonacci algorithm
- `primes-range.vel` — Prime number search
- `lzw.vel` — LZW compression algorithm
- `huffman.vel` — Huffman compression algorithm
- `class.vel` — Class usage examples
- `ext.vel` — Extension function examples
- `http-example.vel` — HTTP request examples
- `filesystem-example.vel` — File operations examples

---

[Previous: Standard Library ←](17-standard-library.md) | [Next: Language Features →](19-language-features.md)

