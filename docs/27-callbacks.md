# Callbacks

A **callback** is a function value with a full void signature — `func[(args…) void]` — that crosses an execution boundary: into another actor, or into native (JVM) host code. The closure never leaves the context that created it; what travels is a handle. Invoking the handle ships the arguments back and runs the body **on the thread that owns the closure**. That single rule is what makes two-way native integration safe by construction.

## The shape of a callback

```velo
func[(int, str) void] cb = func(int code, str message) void {
    term.println(message.con(": ").con(code.str));
    void
};
```

Three requirements for a function type to cross a boundary:

1. **Full signature** — argument types declared: `func[(int) void]`, `func[() void]`. The loose form `func[void]` stays legal for local higher-order code but cannot cross.
2. **Transferable arguments** — each argument type must itself be transferable (see [Actors — What Crosses the Boundary](26-actors.md)).
3. **`void` return** — a callback is a one-way notification. It runs asynchronously on its owner; the invoking side gets no value and does not wait. Results flow back through ordinary `async`/`await` (or another callback). This is also what makes `A awaits B while B calls back into A` deadlock-free: the callback is *posted*, not executed in place.

Violations are rejected at the point of declaration with a `not transferable` error.

## Velo to Velo: callbacks between actors

```velo
actor class Worker() {
    func process(int value, func[(int) void] done) void {
        done(value * 2);    # posts to the owner — returns immediately
        void
    };
};

actor[Worker] w = new Worker();
await async w.process(21, func(int v) void {
    term.println("got: ".con(v.str));   # runs on the main thread
    void
});
term.println("main frame done");        # prints BEFORE "got: 42"
```

The main context is itself an actor ("actor #0"): after its frame completes, the program keeps draining the main mailbox while any callbacks are still held by someone. The example prints `main frame done` first, then `got: 42`, and exits once the callback handle is dropped.

Invocations of one callback are serialised in posting order. A callback that travels back to its owner (`A → B → A`) unwraps into the original closure — invoking it there is a plain, immediate local call.

## Native to Velo: `VeloFunction`

On the JVM side a callback parameter is declared either as a plain Kotlin function type — `cb: (String) -> Unit`, which gives Velo the full signature for compile-time checking and gives the host an ordinary lambda to invoke — or as `core.VeloFunction`, the explicit handle:

```kotlin
class Notifications {
    private var onMessage: VeloFunction? = null

    fun subscribe(cb: VeloFunction) { onMessage = cb }

    fun deliver(text: String) {
        // Any thread may invoke; the body still runs on the owner's thread.
        onMessage?.post(text)
    }
}
```

```velo
native class Notifications() {
    native func subscribe(func[(str) void] cb) void;
};

Notifications n = new Notifications();
n.subscribe(func(str text) void {
    term.println("message: ".con(text));
    void
});
```

`VeloFunction` has two methods:

- `post(args…)` — fire-and-forget, the normal mode for events. Argument count and types are validated eagerly against the declared Velo signature (`Int`/`Float`/`Boolean`/`Byte`/`String`, `List`, `Map`, or another `VeloFunction`); a mismatch throws on the calling thread before anything is shipped.
- `call(args…): CompletableFuture<Any?>` — same, but completion (or failure) is observable. Velo callbacks return void, so the future resolves to `null`. When invoked from the owner's own thread (a native called synchronously from Velo code), the body executes **inline** — no self-deadlock.

A live `VeloFunction` **pins** its owner: the program (or actor) stays serviceable for as long as the host holds the reference. Drop the reference and the owner may shut down. In CLI mode this is the program's exit condition — "main frame finished and nobody can call us any more".

## Errors

Velo has no exception handling: every unhandled runtime error stops the program loudly. Callbacks follow the same rule. A failure inside a `post`-mode callback (no observer) is **program-fatal**; a failure inside `call` completes the returned future exceptionally and the program lives on — someone observed it.

## Embedding: callbacks on the host's main thread

`VeloRuntime.start` runs a program on any host-provided serial executor. Combined with callbacks this is the BDUI shape — every Velo closure executes on the thread you chose:

```kotlin
val runtime = VeloRuntime().register(UiBridge::class)
val program = runtime.start(Bytecode.read(File("app.vbc")), Dispatcher.from { mainHandler.post(it) })
// ... UI events flow through VeloFunction.post(...), Velo code runs on the UI thread
program.stop()
```

One caveat inherited from synchronous `await`: an `await` in the main context blocks its dispatcher — on a UI thread that means blocking the UI. Embedded scripts should prefer callbacks and fire-and-forget `async` over blocking `await` chains.
