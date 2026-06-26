package host

import vm.actors.Dispatcher
import vm.actors.DispatcherFactory
import java.util.concurrent.LinkedBlockingQueue

/**
 * Host (JVM) actor dispatcher: one dedicated daemon thread draining an unbounded
 * task queue — a thread per actor. A **host capability**, not part of the
 * portable VM core (which is cooperative and creates no threads); a host that
 * wants each actor on its own OS thread plugs [ThreadPerActorFactory] in.
 *
 * [close] lets the thread drain everything already queued and then exit.
 * Uncaught task failures are printed but do not kill the thread: actor request
 * handlers translate their own failures into responses, so a throw here is a VM
 * bug, and a half-dead silent actor would be worse than a noisy one.
 */
class ThreadDispatcher(name: String) : Dispatcher {

    private val tasks = LinkedBlockingQueue<Runnable>()

    @Volatile
    private var closed = false

    private val thread: Thread = Thread({ loop() }, name).apply {
        isDaemon = true
        start()
    }

    private fun loop() {
        while (true) {
            val task = if (closed) tasks.poll() ?: return else tasks.take()
            try {
                task.run()
            } catch (ex: Throwable) {
                ex.printStackTrace()
            }
        }
    }

    override fun execute(task: Runnable) {
        tasks.put(task)
    }

    override fun close() {
        closed = true
        tasks.put(Runnable {}) // wake the thread so it can observe `closed`
    }

    override fun joinFor(timeoutMs: Long) = thread.join(timeoutMs)

    override fun isAlive(): Boolean = thread.isAlive
}

/**
 * Host placement giving each spawned actor its own daemon [ThreadDispatcher].
 * Stateless, so a single shared instance is reused. Inject via
 * `VeloRuntime.actorPlacement { ThreadPerActorFactory }` (or pass to `VM`).
 */
object ThreadPerActorFactory : DispatcherFactory {
    override fun create(name: String): Dispatcher = ThreadDispatcher(name)
}
