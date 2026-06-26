package vm

import core.BoundNative
import core.NativeLinker
import core.NativeRegistry

import core.Op

import core.SerializedProgram
import vm.actors.ActorHandle
import vm.actors.ActorRuntime
import vm.actors.CooperativeDispatcherFactory
import vm.actors.DispatcherFactory
import vm.actors.PumpDispatcher
import java.io.PrintStream

class VM(
    private val nativeRegistry: NativeRegistry = NativeRegistry(),
    private val profiler: VMProfiler = VMProfiler(),
    /** Placement strategy for spawned actors. Defaults to cooperative (all
     *  actors share the single event loop, no host threads); pass a host
     *  DispatcherFactory (e.g. a thread pool) for real parallelism. */
    private val dispatcherFactory: DispatcherFactory? = null,
) {

    private var frameLoader: FrameLoader? = null
    private var natives: Array<BoundNative> = emptyArray()
    private var dataClasses: Map<Int, core.DataClassInfo> = emptyMap()

    /**
     * Load a compiled program and link its native pool against the
     * registry. Linking happens here — before any code runs — and reports
     * every unresolved or mismatched native entry in a single error.
     */
    fun load(program: SerializedProgram) {
        frameLoader = GeneralFrameLoader(program.frames.associateBy { it.num })
        natives = NativeLinker.link(program.natives, nativeRegistry)
        dataClasses = program.dataClasses.associateBy { it.frameNum }
    }

    /**
     * Run the program to completion on the calling thread.
     *
     * The program's main context is an ordinary [ActorHandle] ("actor #0")
     * driven by a [PumpDispatcher]: the calling thread first executes the
     * main frame, then keeps draining the main mailbox — host callbacks and
     * cross-actor callback invocations land there — until nothing pins the
     * main context any more (no live callbacks handed out to natives or
     * other actors). For programs that never share a callback the pump
     * exits immediately after the main frame, exactly like the old loop.
     */
    fun run() {
        val frameLoader = frameLoader ?: throw Exception("FrameLoader is not initialized")
        val pump = PumpDispatcher()
        val actorRuntime = ActorRuntime(dispatcherFactory ?: CooperativeDispatcherFactory(pump))
        val main = ActorHandle.main(
            runtime = actorRuntime,
            sharedFrameLoader = frameLoader,
            sharedNativeRegistry = nativeRegistry,
            sharedNatives = natives,
            sharedDataClasses = dataClasses,
            dispatcher = pump,
            profiler = profiler,
        )

        profiler.start()
        try {
            main.requestMain(frameNum = 0)
            pump.pump(
                fatal = { actorRuntime.fatalOrNull() },
                idle = { !actorRuntime.hasHostCallbacks() && !actorRuntime.anyParkedFibers() },
            )
            println("\n✓ Program finished successfully")
        } catch (_: HaltException) {
            println("\n⏹ Program halted by user request")
        } catch (ex: Throwable) {
            val stack = main.ctx.stack
            if (!stack.empty()) {
                val current = stack.peek()
                val op = if (current.pc < current.ops.size) current.ops[current.pc] else current.ops.last()
                printError(ex, op, current, stack)
            } else {
                System.err.println("\n!! Runtime error: ${ex.message ?: ex}")
            }
        } finally {
            actorRuntime.shutdownAll()
        }
        profiler.stop()
        profiler.memoryStats = main.ctx.memory.getStats()
        profiler.printReport()
    }

}

private fun printError(ex: Throwable, op: Op, frame: Frame, stack: Stack<Frame>, out: PrintStream = System.err) {
    out.println()
    out.println("╔══════════════════════════════════════════════════════════════╗")
    out.println("║                      RUNTIME ERROR                           ║")
    out.println("╚══════════════════════════════════════════════════════════════╝")
    out.println()
    out.println("  Error:     ${ex.javaClass.simpleName}")
    out.println("  Message:   ${ex.message ?: "No message"}")
    out.println("  Op: ${op.javaClass.simpleName}")
    out.println("  Address:   ${frame.pc}")
    out.println()
    
    // Print stack trace
    out.println("┌─ Call Stack ─────────────────────────────────────────────────┐")
    
    var depth = 0
    // First print current frame
    printFrameInfo(frame, depth, out)
    depth++
    
    // Then print rest of the stack
    while (!stack.empty()) {
        val f = stack.pop()
        printFrameInfo(f, depth, out)
        depth++
    }
    
    out.println("└──────────────────────────────────────────────────────────────┘")
    out.println()
}

private fun printFrameInfo(frame: Frame, depth: Int, out: PrintStream) {
    val indent = "  " + "│ ".repeat(depth)
    val marker = if (depth == 0) "→" else "↳"
    
    out.println("$indent$marker Frame at address ${frame.pc}")
    
    // Variables (show up to 5)
    if (!frame.vars.empty()) {
        val records = frame.vars.localRecords()
        out.print("$indent  vars: ")
        out.println(records.take(5).mapIndexed { idx, rec ->
            "[$idx]=${renderValue(rec, 20)}"
        }.joinToString(", "))
        if (records.size > 5) {
            out.println("$indent        ... and ${records.size - 5} more")
        }
    }
    
    // Stack (show up to 3)
    if (!frame.subs.empty()) {
        out.print("$indent  stack: ")
        val items = mutableListOf<String>()
        val tempStack = LifoStack<Record>()
        var count = 0
        while (!frame.subs.empty() && count < 3) {
            val rec = frame.subs.pop()
            tempStack.push(rec)
            items.add(renderValue(rec, 15))
            count++
        }
        // Restore stack
        while (!tempStack.empty()) {
            frame.subs.push(tempStack.pop())
        }
        out.println(items.joinToString(" | "))
    }
}

/**
 * A record value for the error dump. Records reachable from a frame may
 * form reference cycles (closures, class instances), so only primitives
 * are rendered through toString; everything else is shown by kind.
 */
private fun renderValue(rec: Record, limit: Int): String {
    val value = try { rec.get<Any>() } catch (_: Exception) { "?" }
    val text = when (value) {
        is Number, is Boolean, is Char, is String, is Unit -> value.toString()
        else -> value.javaClass.simpleName
    }
    return text.take(limit).let { if (it.length == limit) "$it…" else it }
}
