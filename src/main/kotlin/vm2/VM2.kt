package vm2

import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class VM2 {

    private var program: List<Operation> = emptyList()

    public fun load(parser: Parser) {
        val operations = ArrayList<Operation>()
        while (!parser.eof()) {
            val cmd = parser.next() ?: break
            operations.add(cmd)
        }
        program = operations
    }

    public fun run() {
        val dataStack: Stack<Record> = LifoStack()
        val callStack: Stack<Activation> = LifoStack()
        val heap: Heap = ScopedHeap()

        var pc = 0
        var elapsed = 0L
        try {
            val diagSeq = true
            val diagStat = true
            var diagOutput = StringBuilder()
            diagOutput.append("====================================================\n")
            diagOutput.append("===================== Sequence =====================\n")
            diagOutput.append("====================================================\n")
            var t: Long = 0
            val cmdMs = HashMap<String, Long>()
            val cmdCnt = HashMap<String, Long>()
            val time = System.currentTimeMillis()
            while (pc < program.size) {
                val cmd = program[pc]
                if (diagSeq) {
                    diagOutput.append("[$pc] ${cmd.javaClass.name}\n")
                }
                if (diagStat) {
                    t = System.currentTimeMillis()
                }
                pc = cmd.exec(pc, dataStack, callStack, heap)
                if (diagStat) {
                    val e = System.currentTimeMillis() - t
                    val name = cmd.javaClass.name
                    val pe = cmdMs[name] ?: 0
                    cmdMs[name] = pe + e
                    val pi = cmdCnt[name] ?: 0
                    cmdCnt[name] = pi + 1
                }
            }
            if (diagStat) {
                diagOutput.append("====================================================\n")
                diagOutput.append("==================== Statistics ====================\n")
                diagOutput.append("====================================================\n")
                val sortedMs = cmdMs.toList().sortedByDescending { entry ->
                    val times = cmdCnt[entry.first] ?: 0
                    entry.second.toDouble() / times
                }
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
            println("program ended")
        } catch (ignored: HaltException) {
            println("program halted")
        } catch (ex: Throwable) {
            println("exception was thrown on $pc: " + program[pc])
            ex.printStackTrace()
        }
        println("vm stopped in $elapsed ms\n")
    }

}