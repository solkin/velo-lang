package org.velo.android.engine

import core.Bytecode
import core.NativeRegistry
import core.SerializedProgram
import vm.RunStats
import vm.VeloRuntime
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * One terminal run of a compiled Velo program.
 *
 * Loads a `.vbc` and runs it to completion on a dedicated background thread, with
 * its `Terminal` native bound to this session: stdout/stderr stream to [onOutput],
 * stdin is served from [stdin] (the terminal input field). [stop] cancels a
 * waiting `input()` and interrupts the run.
 *
 * Modelled on the NanoVM Android demo's `TerminalSession`.
 */
class VeloTerminalSession(
    private val onOutput: (String) -> Unit,
    private val onFinished: (Throwable?, RunStats?) -> Unit,
) {
    val stdin = HostStdin()

    private val binding = TerminalBinding(onOutput, stdin)
    private var thread: Thread? = null
    @Volatile private var stopped = false

    /** Start running [bytecode] (raw `.vbc`). Returns immediately; runs on a worker thread. */
    fun start(bytecode: ByteArray) {
        val program = readProgram(bytecode)
        val worker = Thread({ run(program) }, "velo-run")
        thread = worker
        worker.start()
    }

    private fun run(program: SerializedProgram) {
        AndroidTerminal.current.set(binding)
        var failure: Throwable? = null
        var stats: RunStats? = null
        try {
            // The default native pool, matching the names the CLI registers so any
            // .vbc written against the standard natives links here. The compiler is
            // never on the device — only these runtime implementations.
            val natives = NativeRegistry()
                .register("Terminal", AndroidTerminal::class)
                .register("Time", VeloTime::class)
                .register("FileSystem", VeloFileSystem::class)
                .register("Http", VeloHttp::class)
                .register("Socket", VeloSocket::class)
            stats = VeloRuntime(natives).run(program)
        } catch (e: InterruptedException) {
            // Stopped by the user — not a failure.
        } catch (e: Throwable) {
            if (!stopped) failure = e
        } finally {
            AndroidTerminal.current.remove()
            onFinished(failure, stats)
        }
    }

    /** Feed a line the user submitted (the newline is added for the guest). */
    fun submitInput(text: String) = stdin.submitLine(text)

    /** Signal end-of-input (Ctrl-D): pending reads drain, then the guest sees EOF. */
    fun endInput() = stdin.signalEof()

    /** Stop the run: unblock a read-waiting guest and interrupt the worker. */
    fun stop() {
        stopped = true
        stdin.cancel()
        thread?.interrupt()
    }

    private fun readProgram(bytecode: ByteArray): SerializedProgram =
        DataInputStream(ByteArrayInputStream(bytecode)).use { Bytecode.read(it) }
}
