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
        val heap = HeapImpl()
        
        // Create VMContext with all subsystems
        val ctx = VMContext(
            stack = stack,
            frameLoader = frameLoader,
            heap = heap,
            nativeArea = NativeImpl(),
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
            println("\nProgram ended")
        } catch (_: HaltException) {
            println("\nProgram halted")
        } catch (ex: Throwable) {
            val op = frame.ops[frame.pc]
            println("\n!! Fatal error on ${frame.pc}: ${op.javaClass.name}: ${ex.message}")
            stack.printStackTrace()
        }
        profiler.stop()
        profiler.heapStats = heap.getStats()
        profiler.printReport()
    }

}

fun Stack<Frame>.printStackTrace(out: PrintStream = System.out) {
    while (!empty()) {
        val frame = pop()
        out.println("\tat addr=${frame.pc}")
        if (frame.subs.empty()) {
            out.println("\t\tframe stack empty")
        } else {
            out.println("\t\tframe stack:")
            while (!frame.subs.empty()) {
                val record = frame.subs.pop()
                out.println("\t\t> ${record.get<Any>()}")
            }
        }
        if (frame.vars.empty()) {
            out.println("\t\tframe vars empty")
        } else {
            out.println("\t\tframe vars:")
            frame.vars.vars.forEach { (index, record) ->
                out.println("\t\t> $index = $record")
            }
        }
    }
}
