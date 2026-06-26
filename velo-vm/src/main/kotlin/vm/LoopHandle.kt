package vm

import vm.actors.ActorRuntime

/**
 * A handle to a running program's cooperative event loop, handed to an embedding host
 * at run start (see [VeloRuntime.run]'s `onStart` / [VM.run]).
 *
 * While the host holds at least one [retain], the loop stays alive even with an empty
 * mailbox and no Velo callbacks outstanding — and exits once every retain is released
 * (and nothing else pins it). This is the general embedding primitive for *"keep the
 * program running while a host-side resource is open"*: a GUI host pins it while a
 * window is on screen, a server while a socket is bound. It reuses the same liveness
 * signal as host-retained callbacks, so it composes with them.
 *
 * Thread-safe — [retain]/[release] may be called from any thread; pair each [retain]
 * with exactly one [release].
 */
class LoopHandle internal constructor(private val runtime: ActorRuntime) {

    /** Pin the loop alive. Balance with [release]. */
    fun retain() = runtime.retainCallback()

    /** Drop one prior [retain]; once nothing pins the loop it may exit. */
    fun release() = runtime.releaseCallback()
}
