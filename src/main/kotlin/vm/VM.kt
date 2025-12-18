package vm

import utils.SerializedFrame
import java.io.PrintStream

class VM(
    private val nativeRegistry: NativeRegistry = NativeRegistry(),
    private val profiler: VMProfiler = VMProfiler()
) {

    private var frameLoader: FrameLoader? = null

    fun load(parser: Parser) {
        val frames = HashMap<Int, SerializedFrame>()
        while (!parser.eof()) {
            val frame = parser.next() ?: break
            frames.put(frame.num, frame)
        }
        frameLoader = GeneralFrameLoader(frames)
    }

    fun run() {
        val stack: Stack<Frame> = LifoStack()
        val frameLoader = frameLoader ?: throw Exception("FrameLoader is not initialized")
        val memory = MemoryAreaImpl()
        
        // Create VMContext with all subsystems
        val ctx = VMContext(
            stack = stack,
            frameLoader = frameLoader,
            memory = memory,
            nativeRegistry = nativeRegistry
        )
        
        var frame = ctx.loadFrame(num = 0, parent = null) ?: throw Exception("No main frame")
        
        profiler.start()
        try {
            ctx.pushFrame(frame)
            while (frame.pc < frame.ops.size) {
                val cmd = frame.ops[frame.pc]
                profiler.beforeOp(cmd)
                frame.pc = cmd.exec(pc = frame.pc, ctx)
                profiler.afterOp()
                frame = ctx.currentFrame()
            }
            println("\n✓ Program finished successfully")
        } catch (_: HaltException) {
            println("\n⏹ Program halted by user request")
        } catch (ex: Throwable) {
            val op = frame.ops[frame.pc]
            printError(ex, op, frame, stack)
        }
        profiler.stop()
        profiler.memoryStats = memory.getStats()
        profiler.printReport()
    }

}

private fun printError(ex: Throwable, op: Operation, frame: Frame, stack: Stack<Frame>, out: PrintStream = System.err) {
    out.println()
    out.println("╔══════════════════════════════════════════════════════════════╗")
    out.println("║                      RUNTIME ERROR                           ║")
    out.println("╚══════════════════════════════════════════════════════════════╝")
    out.println()
    out.println("  Error:     ${ex.javaClass.simpleName}")
    out.println("  Message:   ${ex.message ?: "No message"}")
    out.println("  Operation: ${op.javaClass.simpleName}")
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
        val vars = frame.vars.vars.entries.take(5)
        out.print("$indent  vars: ")
        out.println(vars.joinToString(", ") { (idx, rec) -> 
            val value = try { rec.get<Any>() } catch (_: Exception) { "?" }
            val short = value.toString().take(20).let { if (it.length == 20) "$it…" else it }
            "[$idx]=$short"
        })
        if (frame.vars.vars.size > 5) {
            out.println("$indent        ... and ${frame.vars.vars.size - 5} more")
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
            val value = try { rec.get<Any>() } catch (_: Exception) { "?" }
            items.add(value.toString().take(15).let { if (it.length == 15) "$it…" else it })
            count++
        }
        // Restore stack
        while (!tempStack.empty()) {
            frame.subs.push(tempStack.pop())
        }
        out.println(items.joinToString(" | "))
    }
}
