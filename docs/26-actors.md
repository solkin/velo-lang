# Actors

An **actor** in Velo is a class whose instances live on their own daemon thread. Their state is private — every interaction with an actor goes through serialised method calls. There is no way to read or write an actor's fields directly; the type system makes that mistake a compile-time error.

The model is small and explicit:

- Declare an actor with `actor class`.
- Instantiate with `new ActorClass(args)`. The result is typed `actor[T]`.
- Invoke methods with `await receiver.method(args)`.

That's it. Three pieces of syntax, two new opcodes, no scheduler.

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

term.println((await counter.bump()).str);  # 1
term.println((await counter.bump()).str);  # 2
term.println((await counter.value()).str); # 2
```

`new Counter(0)` produces a value of type `actor[Counter]` — a typed remote handle. Method calls on that handle:

- block the caller thread until the actor responds;
- run on the actor's worker, never on the caller;
- structurally clone serialisable arguments and return values so neither side can poke the other's memory.

`await` is mandatory. Naked `counter.bump()` doesn't compile, because the type `actor[Counter]` exposes no properties — only `await` resolves them.

### Why `await`?

The keyword is a deliberate visual marker that *this call crosses a thread boundary*. Velo doesn't currently have user-level coroutines, so `await` is synchronous: it suspends the calling thread until the actor replies. The signal is the same as in JS/Swift/C#: "something interesting happens here, I'd better notice".

## What Crosses the Boundary

The compiler enforces a static notion of **transferable** types — anything that can either be structurally copied or shipped as a typed handle:

- primitives (`int`, `float`, `byte`, `bool`, `str`)
- `array[T]`, `tuple[…]`, `dict[K:V]` whose element types are themselves transferable
- another actor's `actor[T]` handle
- `void`

Everything else is non-transferable and rejected **at the point of declaration** — not at the call site:

- non-actor `class` instances — they live in a single `MemoryArea` and would race
- function values (`func[T]`) — capture mutable lexical state
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

## Returning Other Actors

An actor's method may return an `actor[T]` — either itself or another actor. The receiver gets a handle pinned to the original actor:

```velo
actor class Container() {
    actor[Counter] inner = new Counter(0);

    func get() actor[Counter] { inner; };
};

actor[Container] box = new Container();
actor[Counter] held = await box.get();

await held.bump();    # 1 — runs on `inner`'s worker, not Container's
await held.bump();    # 2
```

Repeated `await box.get()` yields handles that compare equal — same actor, same internal `objectId`. The second `held` and the first one share state.

## Lifetime

Actor threads are JVM **daemon** threads, so they never block program shutdown. Beyond that, two mechanisms collect them:

1. **GC-driven shutdown.** Every `actor[T]` reference increments a refcount on the actor; a `Cleaner` decrements it when the reference is collected. When the count hits zero, the actor drains its mailbox and exits.
2. **Program-exit hook.** When `vm.VM.run` finishes (or fails) it asks every live actor to shut down, so deterministic cleanup happens before the JVM exits.

There is no built-in `join`/`done`/`terminate` API — actors don't have a "finished" state in the same sense as threads. If you need explicit shutdown for application reasons, expose a `close()` method on your actor and have callers `await` it before dropping their references.

## Identity and Equality

Two `actor[T]` values compare equal when they refer to the same internal object on the same actor:

```velo
actor[Counter] a = await box.get();
actor[Counter] b = await box.get();
# a == b — both point at Container's `inner`
```

The runtime preserves this by maintaining an `objectId ↔ Frame` map per actor: returning the same Velo object twice always reuses the same id.

## Mental Model

Think of an `actor[T]` as a typed channel to a single-threaded service:

- the service's local data is *its* alone, no shared memory;
- the wire format is "method name + cloned args" in, "cloned result" out;
- you cross the wire only at `await`, and the type system makes those crossings impossible to forget.

## Example: Independent Counters

```velo
include "lang/terminal.vel";

actor class Counter(int start) {
    int n = start;
    func bump() int {
        n += 1;
        n;
    };
};

actor[Counter] a = new Counter(10);
actor[Counter] b = new Counter(100);

term.println((await a.bump()).str);  # 11
term.println((await b.bump()).str);  # 101
term.println((await a.bump()).str);  # 12
```

Two actors, two threads, two private `n`s — no `mutex`, no race.

## Restrictions

- `actor class` cannot be `native` — there's nothing on the JVM side to dispatch to.
- `actor class` is not generic. Wrap your generic logic in plain classes and let the actor hold them.
- `await` parses tightly: `await receiver.method(args)`. Use parentheses around the receiver (`await (foo()).method()`) or around the result (`(await x.foo()).str`) when chaining.
- Method-call values (`func[T]`) and pointers cannot cross the boundary. Pass strings/ids and have the actor look up the closure on its side.

---

[Previous: Closures ←](25-closures.md)
