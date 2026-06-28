package core

/**
 * A serial execution domain — the placement SPI for actors.
 *
 * The portable VM core is cooperative and creates no OS threads; running each
 * actor on its own thread (or a shared pool) is a **host capability** plugged
 * in through [DispatcherFactory]. A dispatcher must run submitted tasks one at
 * a time, in submission order, with a happens-before edge between them. That
 * serial guarantee is what makes an actor's mutable state race-free: every
 * message and every resumed `await` continuation for one actor goes through
 * one dispatcher.
 *
 * This contract is intentionally identical to the host dispatchers the CLI and
 * Android demo already ship, so a single implementation serves any VM backend.
 */
interface Dispatcher {

    /** Submit [task] to run serially after everything already queued. */
    fun execute(task: Runnable)

    /** Stop accepting new work; let what is already queued drain, then exit. */
    fun close()

    /** Block up to [timeoutMs] for queued work to finish (best-effort). */
    fun joinFor(timeoutMs: Long)

    /** Whether the dispatcher is still able to run work. */
    fun isAlive(): Boolean
}

/**
 * Creates one [Dispatcher] per spawned actor. Inject via
 * `VeloRuntime.actorPlacement { ... }`; when absent the VM runs every actor on
 * a single shared cooperative loop (no threads), which is the portable default.
 */
interface DispatcherFactory {

    /** Create a dispatcher for an actor; [name] is a debug label. */
    fun create(name: String): Dispatcher

    /** Release any shared resources (e.g. a thread pool) when the program ends. */
    fun shutdown() {}
}
