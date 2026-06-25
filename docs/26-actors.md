# Actors

An **actor** in Velo is a class whose instances live on their own daemon thread. Their state is private — every interaction with an actor goes through serialised method calls. There is no way to read or write an actor's fields directly; the type system makes that mistake a compile-time error.

The model is small and explicit:

- Declare an actor with `actor class`.
- Instantiate with `new ActorClass(args)`. The result is typed `actor[T]`.
- Call it synchronously with `await receiver.method(args)`.
- For parallelism, start a call with `async receiver.method(args)` (typed `future[T]`) and `await` the future later.

The synchronous form `await receiver.method(args)` is sugar for `await async receiver.method(args)`: `async` starts a call and yields a `future[T]`, `await` drains a future, and the sugar simply implies the `async` when you want the result right away.

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

The matching `await` then suspends the caller until that future resolves, and decodes the response into a fresh value in the caller's memory area. A not-yet-ready `await` parks the current fiber and frees the thread rather than blocking it (VEL-11), so the actor — or the main context on a UI thread — keeps servicing its mailbox while parked.

A naked `counter.bump()` doesn't compile, because the type `actor[Counter]` exposes no properties — the call has to be marked as crossing the boundary.

### The synchronous shortcut: `await receiver.method(args)`

The common case — start a call and immediately wait for it — has a one-keyword form: `await` accepts an actor method call directly, and `async` is implied.

```velo
term.println((await counter.bump()).str);        # sugar
term.println((await async counter.bump()).str);  # the same thing, spelled out
```

`await` still does just one thing — produce the result of a cross-thread operation. Reach for a bare `async` (without `await`) only when you want the `future[T]` itself, to overlap work — see [Parallel Work](#parallel-work-with-async).

### Why a visible marker?

`await` is a deliberate signal that *this call crosses a thread boundary* — and a **yield point**. A not-yet-ready `await` suspends the current fiber and lets the actor (or main context) service other messages until the reply arrives (VEL-11, "coroutines without threads"), rather than blocking the thread. The signal is the same as in JS/Swift/C#: "something interesting happens here, I'd better notice" — including that the actor's own state may have advanced while you were parked, since another message can run at the yield point.

### Reentrancy: `await` is a yield point

Because a not-yet-ready `await` suspends the current fiber instead of blocking the thread, the actor (or main context) is free to run **other** mailbox messages while one call is parked. An actor still does exactly one thing at a time — but an `await` is a point where it may switch to another message and resume this one later. The practical consequence: **actor state can change across an `await`.**

```velo
actor class Account() {
    int balance = 100;

    func withdraw(actor[Bank] bank, int amount) bool {
        bool ok = await bank.authorize(amount);   # ← yield point
        # Another message may have run while we were parked here, so re-read
        # state rather than trusting a value captured before the await.
        if (ok && balance >= amount) {
            balance = balance - amount;
            true;
        } else {
            false;
        };
    };
};
```

This is the same model as a JavaScript event loop or coroutines on a single dispatcher. Two habits keep it manageable:

- **Read after the await, not before.** Don't cache actor state across an `await` and assume it is still current — read it again once the await resolves.
- **Keep an invariant on one side of an `await`.** If two field updates must be atomic with respect to other messages, don't put an `await` between them.

The flip side is the same coin: a `void` callback or an un-awaited `async` posted to an actor runs at the next yield point or after the current message finishes — never in the middle of straight-line code.

What does **not** yield: an `await` reached inside a synchronously-invoked callback (a value-returning callback running inline on its owner) still blocks the thread, because that call lives on the JVM stack and cannot be parked. Likewise, constructing an actor whose constructor `await`s blocks the spawning code until construction finishes.

## What Crosses the Boundary

The compiler enforces a static notion of **transferable** types — anything that can either be structurally copied or shipped as a typed handle:

- primitives (`int`, `float`, `byte`, `bool`, `str`)
- `array[T]`, `tuple[…]` whose element types are themselves transferable
- [`data class`](28-data-classes.md) values — immutable structs, copied by value
- another actor's `actor[T]` handle
- callbacks — a fully-signed `func[(args…) ret]`: the closure stays with its
  owner, the receiver gets a handle, and invoking it runs on the owner's
  thread. A `void` return is a fire-and-forget notification; a value return is
  a blocking call back to the owner (see [Callbacks](27-callbacks.md))
- `void`

Everything else is non-transferable and rejected **at the point of declaration** — not at the call site:

- non-actor `class` instances (including `dict`/`Map`) — they live in a single
  `MemoryArea` and would race if shared
- loose function values (`func[T]`, no argument signature)
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

To share structured data, make it a [`data class`](28-data-classes.md) (copied by value) or a tuple; to share an object *identity*, wrap it as another `actor class`.

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
- `await receiver.method(args)` is the synchronous sugar (`async` implied). When the awaited value is a plain `future[T]`, `await` accepts any expression but only that type — `await someInt` is a compile error.
- `await` shares precedence with `.` / `[]` / `()`, so `await arr[i]` parses as `(await arr)[i]`. When the future comes from indexing, calling, or any other postfix, wrap it: `await (arr[i])`, `await (someFunc())`.
- `future[T]` is pinned to its producing actor. It cannot appear in another `actor class`'s method signatures (param or return).
- Loose function values (`func[T]`) and pointers cannot cross the boundary. A fully-signed `func[(int) str]` *can*: it travels as a callback run on its owner's thread — `void`-returning ones are fire-and-forget, value-returning ones block for the result (see [Callbacks](27-callbacks.md)).

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

[Previous: Closures ←](25-closures.md) | [Next: Callbacks →](27-callbacks.md)
