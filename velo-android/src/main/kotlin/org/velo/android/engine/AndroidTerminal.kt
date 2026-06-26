package org.velo.android.engine

/**
 * The per-run terminal wiring: where the guest's output goes and where its input
 * comes from. Bound to the execution thread via [AndroidTerminal.current] just
 * before a program runs.
 */
class TerminalBinding(
    val onOutput: (String) -> Unit,
    val stdin: HostStdin,
) {
    /** Reads one line (without the trailing newline) from stdin; "" at EOF/stop. */
    fun readLine(): String {
        val sb = StringBuilder()
        val one = ByteArray(1)
        while (true) {
            val n = stdin.read(one, 0, 1)
            if (n <= 0) break
            val c = one[0].toInt().toChar()
            if (c == '\n') break
            if (c != '\r') sb.append(c)
        }
        return sb.toString()
    }
}

/**
 * Host implementation of the Velo `Terminal` native used by the bundled samples
 * (`print` / `println` / `input`). Registered under the Velo name `Terminal`, so
 * compiled `.vbc` written against the standard terminal links against this one,
 * routing stdout to the on-screen terminal and stdin to its input field instead
 * of the host process's own streams.
 *
 * The VM constructs this with the no-arg constructor on the execution thread; it
 * reads the active [TerminalBinding] from a thread-local that the session sets up
 * for the duration of the run.
 */
class AndroidTerminal {
    private fun io(): TerminalBinding =
        current.get() ?: error("Terminal native used outside a Velo run")

    fun print(text: String) = io().onOutput(text)

    fun println(text: String) = io().onOutput(text + "\n")

    fun input(): String = io().readLine()

    companion object {
        /** The terminal binding for the run executing on the current thread. */
        val current = ThreadLocal<TerminalBinding>()
    }
}
