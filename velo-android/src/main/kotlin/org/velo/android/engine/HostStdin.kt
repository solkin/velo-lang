package org.velo.android.engine

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A blocking byte channel between the terminal UI (producer) and the Velo
 * `Terminal.input()` native (consumer, running on the execution thread).
 *
 * [read] blocks until bytes are available, end-of-input is signalled, or the
 * channel is cancelled — mirroring how a real `System.in.read` parks until the
 * user types. The UI feeds it with [submitLine] as the user enters text.
 *
 * Adapted from the NanoVM Android demo's terminal stdin.
 */
class HostStdin {
    private val lock = ReentrantLock()
    private val notEmpty = lock.newCondition()
    private val buffer = ArrayDeque<Byte>()
    private var eof = false

    /** Blocking read of up to [len] bytes into [dst] at [off]; returns the count, or -1 at end of input. */
    fun read(dst: ByteArray, off: Int, len: Int): Int = lock.withLock {
        if (len == 0) return 0
        while (buffer.isEmpty() && !eof) notEmpty.await()
        if (buffer.isEmpty() && eof) return -1
        var i = 0
        while (i < len && buffer.isNotEmpty()) {
            dst[off + i] = buffer.removeFirst()
            i++
        }
        i
    }

    fun submit(bytes: ByteArray) = lock.withLock {
        for (b in bytes) buffer.addLast(b)
        notEmpty.signalAll()
    }

    /** Submit a line plus the newline the terminal input field appends on Enter. */
    fun submitLine(line: String) = submit((line + "\n").toByteArray(Charsets.UTF_8))

    /** No more input will arrive: reads drain the buffer, then return -1 (EOF). */
    fun signalEof() = lock.withLock {
        eof = true
        notEmpty.signalAll()
    }

    /** Abrupt stop: unblock any waiting read immediately by behaving as EOF. */
    fun cancel() = signalEof()
}
