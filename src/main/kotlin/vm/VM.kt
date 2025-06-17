package vm

import utils.SerializedFrame
import java.io.PrintStream

class VM {

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
        var elapsed = 0L
        val frameLoader = frameLoader ?: throw Exception("FrameLoader is not initialized")
        var frame = frameLoader.loadFrame(num = 0, parent = null) ?: throw Exception("No main frame")
        val diagSeq = false
        val diagStat = false
        val diagInfo = false
        try {
            val diagOutput = StringBuilder()
            if (diagSeq) {
                diagOutput.append("-- Sequence\n")
            }
            var diagMs: Long = 0
            val cmdMs = HashMap<String, Long>()
            val cmdCnt = HashMap<String, Long>()
            val time = System.currentTimeMillis()
            stack.push(frame)
            while (frame.pc < frame.ops.size) {
                val cmd = frame.ops[frame.pc]
                if (diagSeq) {
                    diagOutput.append("[${frame.pc}] ${cmd.javaClass.name}\n")
                }
                if (diagStat) {
                    diagMs = System.currentTimeMillis()
                }
                frame.pc = cmd.exec(pc = frame.pc, stack, frameLoader)
                if (diagStat) {
                    val e = System.currentTimeMillis() - diagMs
                    val name = cmd.javaClass.name
                    val pe = cmdMs[name] ?: 0
                    cmdMs[name] = pe + e
                    val pi = cmdCnt[name] ?: 0
                    cmdCnt[name] = pi + 1
                }
                frame = stack.peek()
            }
            if (diagStat) {
                diagOutput.append("-- Statistics\n")
                val sortedMs = cmdMs.toList().sortedByDescending { it.second }
                for (entry in sortedMs) {
                    val times = cmdCnt[entry.first] ?: 0
                    val mil: Double = entry.second.toDouble() * 1000000000 / times.toDouble()
                    diagOutput.append(entry.first.padEnd(30, '.') + "$times times".padEnd(15, ' ') + "/ ${entry.second} ms".padEnd(8, ' ') + "-> ${mil.toInt()} ms/bil\n")
                }
            }
            if (diagOutput.isNotBlank()) {
                println(diagOutput.toString())
            }
            elapsed = System.currentTimeMillis() - time
            println("\nProgram ended")
        } catch (_: HaltException) {
            println("\nProgram halted")
        } catch (ex: Throwable) {
            val op = frame.ops[frame.pc]
            println("\n!! Fatal error on ${frame.pc}: ${op.javaClass.name}: ${ex.message}")
            stack.printStackTrace()
            if (diagInfo) {
                ex.printStackTrace()
            }
        }
        println("VM stopped in $elapsed ms")
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
