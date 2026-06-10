# Actors

An **actor** in Velo is a class whose instances live on their own daemon thread. Their state is private — every interaction with an actor goes through serialised method calls. There is no way to read or write an actor's fields directly; the type system makes that mistake a compile-time error.

The model is small and explicit:

- Declare an actor with `actor class`.
- Instantiate with `new ActorClass(args)`. The result is typed `actor[T]`.
- Start a method with `async receiver.method(args)`. The result is typed `future[T]`.
- Block on a future with `await futureExpr`.

The synchronous shorthand is `await async receiver.method(args)`. There is no other "synchronous call" syntax — `await` always means "drain a future", `async` always means "start one", and the two compose.

## Declaring an Actor

```velo
actor class Counter(int start) {
    int n = start;

    func bump() int {
        n += 1;
        n;
    };

    func value() int { n; };
};
```

The body of an `actor class` is identical to a regular class — fields, methods, init code. The only difference is that constructing an instance spins up a new worker thread and runs the constructor on it.

## Spawning and Calling

```velo
actor[Counter] counter = new Counter(0);

term.println((await async counter.bump()).str);  # 1
term.println((await async counter.bump()).str);  # 2
term.println((await async counter.value()).str); # 2
```

`new Counter(0)` produces a value of type `actor[Counter]` — a typed remote handle. The `async ... .method(args)` step does three things at runtime:

- structurally clones the arguments so the actor cannot see caller memory;
- posts a `Call` message to the actor's mailbox;
- pushes a `future[T]` value (no waiting yet — the call site stays running).

The matching `await` then blocks the caller's thread until that future resolves and decodes the response into a fresh value in the caller's memory area.

`async` is mandatory. Naked `counter.bump()` doesn't compile, because the type `actor[Counter]` exposes no properties — only `async` resolves them. `await` on its own doesn't compile either, because it only consumes futures.

### Why two keywords?

The pair is a deliberate visual marker that *this call crosses a thread boundary*. Velo does not have user-level coroutines, so `await` is synchronous: it suspends the calling thread until the actor replies. The signal is the same as in JS/Swift/C#: "something interesting happens here, I'd better notice". Splitting it into `async` (start) and `await` (wait) keeps the cost visible *and* unlocks parallelism — see [Parallel Work](#parallel-work-with-async) below.

## What Crosses the Boundary

The compiler enforces a static notion of **transferable** types — anything that can either be structurally copied or shipped as a typed handle:

- primitives (`int`, `float`, `byte`, `bool`, `str`)
- `array[T]`, `tuple[…]`, `dict[K:V]` whose element types are themselves transferable
- another actor's `actor[T]` handle
- fully-signed void functions — `func[(args…) void]` — shipped as **callbacks**:
  the closure stays with its owner, the receiver gets a handle, and invoking it
  posts the call back to the owner's thread (see [Callbacks](27-callbacks.md))
- `void`

Everything else is non-transferable and rejected **at the point of declaration** — not at the call site:

- non-actor `class` instances — they live in a single `MemoryArea` and would race
- loose function values (`func[T]`, no argument signature) and functions returning
  a value — a callback is a one-way notification; results flow back through
  ordinary `async`/`await`
- pointers (`ptr[T]`) — refer to specific var slots
- `any`, generics, native (JVM) objects — no defined wire format

```velo
class Pair(int a, int b) {};

actor class Bad() {
    func make() Pair { new Pair(1, 2); };  # compile error:
    # Actor method 'Bad.make' return type has type 'Pair',
    # which is not transferable across an actor boundary
};
```

To share structured data, use a tuple/dict; to share an object identity, wrap it as another `actor class`.

## Parallel Work with `async`

`async` returns immediately, so two computations on different actors run concurrently:

```velo
actor[Worker] a = new Worker();
actor[Worker] b = new Worker();

future[int] fa = async a.compute(input1);   # both calls dispatched
future[int] fb = async b.compute(input2);   # before either blocks
int x = await fa;                            # wall time ≈ max(a, b),
int y = await fb;                            # not a + b
```

The same pattern works inside an actor for fan-out:

```velo
actor class Coordinator() {
    actor[Worker] w1 = new Worker();
    actor[Worker] w2 = new Worker();

    func process(int p, int q) tuple[int, int] {
        future[int] fp = async w1.compute(p);
        future[int] fq = async w2.compute(q);
        new tuple(await fp, await fq);
    };
};
```

The `Coordinator.process` method is itself synchronous from the outside (the caller does `await async coordinator.process(...)`), but internally it gets parallel execution on `w1` and `w2`.

`future[T]` is **not** transferable across actor boundaries — it's pinned to the actor that completes it. Trying to pass or return a future from an actor method is a compile error. (Want to expose parallelism through a façade actor? Have it expose `actor[T]` handles instead and let the caller call `async` itself.)

## Returning Other Actors

An actor's method may return an `actor[T]` — either itself or another actor. The receiver gets a handle pinned to the original actor:

```velo
actor class Container() {
    actor[Counter] inner = new Counter(0);

    func get() actor[Counter] { inner; };
};

actor[Container] box = new Container();
actor[Counter] held = await async box.get();

await async held.bump();    # 1 — runs on `inner`'s worker, not Container's
await async held.bump();    # 2
```

Repeated `await async box.get()` yields handles that compare equal — same actor, same internal `objectId`. The second `held` and the first one share state.

## Lifetime

Actor threads are JVM **daemon** threads, so they never block program shutdown. Beyond that, two mechanisms collect them:

1. **GC-driven shutdown.** Every `actor[T]` reference increments a refcount on the actor; a `Cleaner` decrements it when the reference is collected. When the count hits zero, the actor drains its mailbox and exits.
2. **Program-exit hook.** When `vm.VM.run` finishes (or fails) it asks every live actor to shut down, so deterministic cleanup happens before the JVM exits.

There is no built-in `join`/`done`/`terminate` API — actors don't have a "finished" state in the same sense as threads. If you need explicit shutdown for application reasons, expose a `close()` method on your actor and have callers `await` it before dropping their references.

## Identity and Equality

Two `actor[T]` values compare equal when they refer to the same internal object on the same actor:

```velo
actor[Counter] a = await async box.get();
actor[Counter] b = await async box.get();
# a == b — both point at Container's `inner`
```

The runtime preserves this by maintaining an `objectId ↔ Frame` map per actor: returning the same Velo object twice always reuses the same id.

## Mental Model

Think of an `actor[T]` as a typed channel to a single-threaded service:

- the service's local data is *its* alone, no shared memory;
- the wire format is "method name + cloned args" in, "cloned result" out;
- `async` puts the call on the wire and gives you a future; `await` collects the response off the wire.

## Example: Independent Counters

```velo
Terminal term = new Terminal();

actor class Counter(int start) {
    int n = start;
    func bump() int {
        n += 1;
        n;
    };
};

actor[Counter] a = new Counter(10);
actor[Counter] b = new Counter(100);

term.println((await async a.bump()).str);  # 11
term.println((await async b.bump()).str);  # 101
term.println((await async a.bump()).str);  # 12
```

Two actors, two threads, two private `n`s — no `mutex`, no race.

## Restrictions

- `actor class` cannot be `native` — there's nothing on the JVM side to dispatch to.
- `actor class` is not generic. Wrap your generic logic in plain classes and let the actor hold them.
- `async` parses tightly: `async receiver.method(args)`. Use parentheses around the receiver (`async (foo()).method()`) or around the result (`(await async x.foo()).str`) when chaining.
- `await` parses any expression but only accepts `future[T]` types. `await someInt` is a compile error.
- `await` shares precedence with `.` / `[]` / `()`, so `await arr[i]` parses as `(await arr)[i]`. When the future comes from indexing, calling, or any other postfix, wrap it: `await (arr[i])`, `await (someFunc())`.
- `future[T]` is pinned to its producing actor. It cannot appear in another `actor class`'s method signatures (param or return).
- Loose function values (`func[T]`) and pointers cannot cross the boundary. Functions with a full void signature — `func[(int) void]` — *can*: they travel as callbacks executed on their owner's thread (see [Callbacks](27-callbacks.md)).

### Errors in unawaited futures are silently dropped

If you start an asynchronous call with `async` but never `await` the resulting `future[T]`, and the actor's method throws, **no one observes the error**. The completion is recorded inside the underlying `CompletableFuture`, but with no awaiter the failure is lost when the `FutureRecord` is garbage-collected (just as JVM `CompletableFuture` does).

```velo
async worker.boom();   # if boom() throws, the error vanishes
```

This is intentional — fire-and-forget is a useful pattern for void operations — but it means you should `await` any future whose completion you care about, even when you don't need the value. For void methods that's:

```velo
await async worker.notify();   # ensures errors propagate to the caller
```

If a future does need to be discarded knowingly (e.g. you're broadcasting to many actors and don't want any single failure to block), make that explicit in the calling code with a comment. A future warning facility may be added later if this pattern proves error-prone in real programs.

---

[Previous: Closures ←](25-closures.md)
