package org.velo.android.engine.ui

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch

/**
 * Per-run wiring for the `Ui` native: the [UiHost] to drive and a bridge from the
 * Velo worker thread onto the Android main thread. Bound to the execution thread
 * via [UiBinding.current] for the duration of a run, exactly like the terminal's
 * `AndroidTerminal.current`.
 *
 * The Velo program runs off the main thread, but every `View` operation must happen
 * on it — so each `Ui`/`View` native method wraps its Android work in [onUi], which
 * runs inline when already on the main thread and otherwise posts to the main looper
 * and blocks the worker until it completes (a Swing `invokeAndWait`). The block is
 * worker→main only; UI events never block on the worker, so the two never deadlock.
 */
class UiBinding(
    val host: UiHost,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {

    /** Run [block] on the Android main thread and return its result, blocking the caller. */
    fun <T> onUi(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val latch = CountDownLatch(1)
        var result: T? = null
        var failure: Throwable? = null
        mainHandler.post {
            try {
                result = block()
            } catch (e: Throwable) {
                failure = e
            } finally {
                latch.countDown()
            }
        }
        latch.await()
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    companion object {
        /** The UI binding for the run executing on the current thread. */
        val current = ThreadLocal<UiBinding>()
    }
}
