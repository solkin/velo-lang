# Velo Lang — Agent Guide

## Project Overview

Velo Lang is a functional, strictly-typed, compilable programming language that runs on a minimal stack-based virtual machine (53 operations). It is designed to be embeddable in JVM applications.

Key characteristics:
- **Source language**: Velo (`.vel` files)
- **Bytecode format**: `.vbc` (custom format, v12: magic `0x5e10`, native pool + frames)
- **Implementation language**: Kotlin (JVM)
- **License**: MIT (Igor Solkin)

Notable language features:
- Strict typing with generics (no variance annotations, no type constraints)
- Higher-order functions, lambdas, and lexical closures
- Classes with operator overloading and nested classes; extension functions
- `dict[K:V]` is pure compiler sugar over the stdlib `Map` class (`std/map.vel`) — the VM has no dict; the parser auto-imports the implementation when dict syntax is used
- Actor concurrency model with `async`/`await` and `future[T]`; values cross actor boundaries only if transferable (primitives, arrays, tuples — a dict travels as `d.arr` → `array[tuple[K,V]]`)
- Native class binding by registration: types synthesized from plain JVM classes, two-way callbacks via `VeloFunction`
- Pointer types (`ptr[T]`) with address-of and dereference operators
- Standard library written in pure Velo (`std/*.vel`, bundled with the compiler) plus native bindings for Terminal, Time, HTTP, FileSystem and Socket

## Technology Stack

| Component | Version / Details |
|-----------|-------------------|
| Language | Kotlin (JVM), plugin 1.8.0 |
| Gradle | 8.8 (Kotlin DSL) |
| JVM Toolchain | 11 |
| Test Framework | JUnit Platform via `kotlin("test")` |
| Code Style | Kotlin Official (`kotlin.code.style=official`) |

Minimal external dependencies: Kotlin stdlib and JUnit only.

## Modules

```
velo-core      Shared contracts: Op (57 opcodes), VmType, Bytecode (.vbc read/write),
               SerializedProgram/SerializedFrame, NativeRegistry/NativeDescriptor/NativeLinker
velo-compiler  Front-end (depends on core): lexer, Pratt parser, AST nodes, type system,
               codegen; stdlib sources in src/main/resources/std/
velo-vm        Back-end (depends on core): interpreter, records/memory, actors,
               NativeBridge, VeloRuntime (embedding API) — the VM the CLI ships
velo-vm2       Clean-room VM reimplemented from the .vbc spec, verified against the
               golden tests; test-only parity gate (the CLI does not ship it)
velo-cli       CLI (depends on compiler + vm): Main.kt, default native classes
               (Terminal, Time, FileSystem, Http, Socket), demo programs, integration tests
```

## Build and Run Commands

```bash
./gradlew build                  # compile + test everything
./gradlew test                   # tests only
./gradlew :velo-cli:run --args="path/to/program.vel"           # compile and run
./gradlew :velo-cli:run --args="path/to/program.vel out.vbc"   # compile to bytecode
./gradlew :velo-cli:run --args="path/to/program.vbc"           # run pre-compiled bytecode
./gradlew :velo-cli:assembleDist # distribution (tar/zip + launch scripts)
```

Demo programs live in `velo-cli/src/main/resources/*.vel` (some are interactive and read stdin via `term.input()`).

## Compiler (`velo-compiler`)

1. **Lexer** — `compiler/parser/TokenStream.kt` hand-tokenizes source.
2. **Parser** — `compiler/parser/PrattParser.kt` + `Parser.kt`; parselets live in `compiler/parser/parselets/` (literals, operators, postfix, statements). Grammar wiring: `VeloGrammar.kt`, precedences: `Precedence.kt`.
3. **AST** — `compiler/nodes/Node.kt` base with `compile(ctx): Type`; ~30 node types.
4. **Codegen** — `Context.kt` + `CompilerFrame.kt` manage frames, scopes and op emission.
5. **API** — `VeloCompiler.kt`: `.vel` → `SerializedProgram`, native classes registered against a `NativeRegistry`.

### Stdlib and imports

- `import "path"` loads a module through `DependencyLoader` (`compiler/parser/FileInput.kt`); the `.vel` extension is optional. `std/` names resolve from the **compiler classpath** (`velo-compiler/src/main/resources/std/`); other paths resolve from the file system **relative to the importing file** (threaded via `PrattParser.currentDir`). Each module loads once, keyed by canonical path. A module is parsed **in isolation** (its own `TokenStream`, sharing the `ParserContext`) by `ImportParselet`, not spliced into the token stream — so newline/`;` termination in the two files never interferes.
- Dict lowering: `dict[K:V]` parses as `ClassType("Map", [K,V])` (`TypeParser.kt`), literals desugar to `Map()` + `put` calls (`nodes/DictNode.kt`), and `Parser.parse()` prepends the auto-imported `std/map.vel` when dict syntax was used. The hardcoded type-param names `["K","V"]` must match the `class Map[K, V]` declaration in `std/map.vel`.

## Virtual Machine (`velo-vm`)

1. **VM** — `VM.kt` loads frames and runs; `VMExecutor.kt` drives the loop; `Interpreter.kt` is the single `when`-dispatch over all 53 ops.
2. **Memory** — `MemoryArea.kt` heap; `Record` subclasses (`ValueRecord`, `RefRecord` array/class/native, `FuncRecord`, `PtrRecord`) hold values.
3. **Actors** — `vm/actors/`: `ActorRuntime`, `ActorHandle` (isolated VMContext + serial dispatcher), `Dispatcher` (thread / pump / host executor), `StructuredClone` (transferable values only), `VeloFunction`/`CallbackRecord` two-way callbacks, `Pins` refcount machinery.
4. **Native interop** — `NativeRegistry`/`NativeDescriptor` (core) synthesize descriptors from JVM classes once; `NativeLinker` links a program's native pool at load; `NativeBridge.kt` is the single Velo⇄JVM conversion point; `Op.NativeCall` dispatches via MethodHandle by pool index. `java.util.Map` does not cross the boundary (no dict in the VM).
5. **Embedding** — `VeloRuntime.kt`: compile/run/start, register native classes.

## Testing

```bash
./gradlew test
./gradlew :velo-cli:test --tests "integration.ConformanceTest"
```

- `velo-core`: `BytecodeRoundTripTest` — every op and VmType round-trips through `.vbc` (keep its op list in sync with `Op.kt`).
- `velo-compiler`: `ParserTest`, `TokenStreamTest`, `StringInputTest`, `InputStackTest`.
- `velo-vm`: `vm/VMTest`, `HeapTest`, `LifoStackTest`, `VarsTest`, `vm/operations/*` op unit tests.
- `velo-cli`: integration — `ConformanceTest` (runs the language-neutral `/conformance` corpus on **both** `velo-vm` and `velo-vm2`, asserting each case matches its `.out` and the two agree), plus `ActorsTest`, `CallbacksTest`, `DataClassTest`, `NativeBindingTest`, `ImportTest`, `Vm2ParityTest`.

To add a conformance case: drop `myfeature.vel` + `myfeature.out` into `conformance/cases/<category>/` (byte-exact expected stdout). A missing `.out` is auto-recorded on first run when both VMs agree and no `FAIL` self-check line prints, then the run fails asking for review. See `conformance/README.md`.

## CI / CD

One workflow (`.github/workflows/pages.yml`): regenerates the site from `site/` and deploys to GitHub Pages. **There is no build/test CI** — run `./gradlew test` locally before considering a change done.

## Code Style

- Kotlin Official style; no star imports.
- Velo source conventions (`docs/20-best-practices.md`): lowercase functions, uppercase classes, prefer explicit types over `any`, split large programs with `import`.

## Security Considerations

- The VM executes arbitrary bytecode — be cautious loading untrusted `.vbc` in embeddings.
- Any class registered via `NativeRegistry`/`VeloRuntime.register()` exposes its public methods to Velo code; do not register sensitive JVM classes in untrusted contexts.
- Actor messages are deep-cloned (`StructuredClone`); non-transferable types are rejected at compile time in `actor class` signatures and at runtime as a safety net.

## Tips for Agents

- New AST node: extend `Node`, implement `compile(ctx): Type`; register a parselet in `VeloGrammar.kt` (precedence in `Precedence.kt`).
- New VM op: add to `core/Op.kt` (pick a free opcode byte), handle it in `vm/Interpreter.kt`, add read/write support in `core/Bytecode.kt`, list it in `BytecodeRoundTripTest`. Think twice — the project goal is a *minimal* ISA; prefer expressing features in pure Velo stdlib (`velo-compiler/src/main/resources/std/`) like dict/Map does.
- New stdlib module: put `name.vel` under `velo-compiler/src/main/resources/std/`; users get it via `import "std/name";`.
- Changing the `.vbc` layout or opcode bytes: bump `Bytecode.VERSION_MAJOR`.
- `docs/` holds the language reference (27 chapters); `site/` is the generated website.
