package integration

import core.NativeMappingException
import core.NativeRegistry
import core.VmType
import vm.VM
import vm.VMContext
import vm.VMExecutor
import vm.VMProfiler
import vm.VeloProgram
import vm.VeloRuntime

import core.VeloFunction

/**
 * Native test double for callback interop: captures the [VeloFunction]
 * handed over from Velo code and lets the JUnit side (or a background
 * thread) invoke it. The capture is static so the *test* can reach it after
 * (or while) the program runs; tests must call [reset] when done, otherwise
 * the captured pin keeps the program's main context alive forever.
 */
class TestBridge {

    fun register(cb: VeloFunction) {
        cb.retain() // held for later async firing — keep the event loop alive
        captured = cb
    }

    /** Record the thread the surrounding Velo code is executing on. */
    fun mark() {
        invokeThread = Thread.currentThread().name
    }

    /** Post [value] to the captured callback from a fresh background thread. */
    fun fireFromBackground(value: Int) {
        val cb = captured ?: error("no callback registered")
        Thread({ cb.post(value) }, "test-background").start()
    }

    /**
     * Invoke the captured callback synchronously from the calling (owner)
     * thread — exercises the inline path of [VeloFunction.call].
     */
    fun fireInline(value: Int) {
        val cb = captured ?: error("no callback registered")
        cb.call(value).join()
    }

    /** Drop the static capture so the program's main context can shut down. */
    fun release() {
        captured?.release()
        captured = null
    }

    companion object {
        @Volatile
        var captured: VeloFunction? = null

        @Volatile
        var invokeThread: String? = null

        fun reset() {
            captured?.release()
            captured = null
            invokeThread = null
        }
    }
}
