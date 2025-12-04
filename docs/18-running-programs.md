# Running Programs

## Running from Resources

Programs from the `src/main/resources` directory can be run with the `res://` prefix:

```bash
./gradlew run --args="res://hello.vel"
./gradlew run --args="res://primes-range.vel"
```

## Running from File

To run a program from a file, use the `file://` prefix:

```bash
./gradlew run --args="file:///path/to/program.vel"
```

## Example Programs

The project includes many examples:

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

