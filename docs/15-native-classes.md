# Native Classes

Native classes are plain JVM (Kotlin/Java) classes exposed to Velo. There is no declaration on the Velo side at all: **registering the class on the runtime is the single source of truth**. The compiler synthesizes the Velo type from the class itself, checks every call against it, and the VM links all native entry points before the first opcode runs.

## Binding a Class

Host side — any plain class, no annotations:

```kotlin
class Counter(start: Int) {
    private var n = start
    fun bump(): Int = ++n
    fun value(): Int = n
}

val natives = NativeRegistry()
    .register(Counter::class)              // Velo name = "Counter"
    .register("Ticker", Clock::class)      // custom Velo name

val compiler = VeloCompiler(natives)       // compile-time checking
val runtime = VeloRuntime(natives)         // load-time linking
```

The same registry serves both sides; `VeloCompiler` and `VeloRuntime` also
expose `register(...)` directly when only one of them is in the process.

Velo side — just use it:

```velo
Counter c = new Counter(10);
term.println(c.bump().str);   # 11
term.println(c.value().str);  # 11
```

The CLI registers `Terminal`, `Time`, `FileSystem`, `Http` and `Socket` for
every program it runs; an embedding application picks its own set. A program
just constructs what it needs (`Terminal term = new Terminal();`) — native
classes have no `include`.

## How It Works

1. **Registration** stores a Velo-name → JVM-class mapping. On first use the registry introspects the class once: public declared methods, the single public constructor, signatures mapped to Velo types, `MethodHandle`s resolved. No KSP, no codegen, no per-call reflection.
2. **Compilation** resolves `new Counter(...)` and `c.bump()` against that descriptor — argument counts and types are verified at compile time — and interns each used entry point into the program's **native pool**.
3. **Loading** links the pool against the host registry (`.vbc` carries the pool by Velo names, so the same bytecode links against different host implementations). Every missing or mismatched entry is reported in one error, before execution.
4. **Calling** is a direct `MethodHandle` invocation through a single `NativeCall` opcode.

## Binding Rules

- Exactly **one public constructor**.
- Every **public declared method** is exposed; method names must be unique (Velo has no overloads). Inherited `Object` methods are not exposed.
- Every signature must be expressible in Velo types (see below). Violations are reported at registration/compile time — all problems at once.

## Type Mapping: Velo ⇄ JVM

| Velo | JVM parameter / return |
|------|------------------------|
| `int` | `Int` |
| `float` | `Float` |
| `bool` | `Boolean` |
| `byte` | `Byte` |
| `str` | `String` |
| `array[T]` | `Array<T>` (boxed), `ByteArray`/`IntArray`/... for returns, `List<T>` |
| `dict[K:V]` | `Map<K, V>` |
| registered native class | the class itself |
| `func[(args) void]` | `VeloFunction` or Kotlin `(args) -> Unit` |
| `void` | `Unit` / `void` |

Native instances are opaque handles in Velo — no fields, only method calls. They can be passed to and returned from other native methods freely:

```velo
Socket client = server.accept();   # returns a new native instance
time.print(term);                  # passes one native to another
```

They cannot cross actor boundaries (an actor must create its own). Extension functions work on native types like on any other:

```velo
ext(Terminal t) printInt(int a) void {
    t.println(a.str);
};
term.printInt(42);
```

## Callbacks: native code calling Velo

A `func[(args) void]` argument arrives on the host either as an explicit `VeloFunction` (`post`/`call` from any thread) or as a plain Kotlin function value when the parameter is declared as one:

```kotlin
class Notifications {
    fun subscribe(cb: (String) -> Unit) { onMessage = cb }
}
```

```velo
Notifications n = new Notifications();
n.subscribe(func(str text) void {
    term.println("message: ".con(text));
    void
});
```

The Kotlin-function form carries the full signature, so the compiler checks the Velo lambda against it. Either way the body always executes on the thread that owns the closure. See [Callbacks](27-callbacks.md) for the whole model.

## Satisfying interfaces

Because a native class is bound by **shape**, it can satisfy a Velo [interface](29-interfaces.md) with no annotation or wrapper on the host side — exactly like a Velo class. If the registered class has methods whose names and signatures match the interface, a native handle can be assigned to an interface-typed variable, passed to a function expecting the interface, or dropped into an `array[Interface]` alongside Velo instances. Calls dispatch by name across the native boundary, and a fluent host method that returns itself fulfils a [`Self`](29-interfaces.md#self-return-type)-returning requirement.

```velo
# A registered host class with `area() int` and `kind() str` satisfies this:
interface Shape { func area() int; func kind() str; };

Widget w = new Widget("hello");
Shape s = w;
term.println(s.kind());        # widget:hello
term.println(s.area().str);    # 5
```

## Errors

- Unregistered class, missing method, wrong signature → compile-time error.
- Program loaded into a host missing some registrations → one load-time error listing **every** unresolved entry.
- A native method throwing at runtime → Velo runtime error tagged with the native entry (`Native call Http.get(Str) failed: ...`).
