package vm2

import java.util.*
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
        val time = System.currentTimeMillis()

        val dataStack: Stack<Record> = LifoStack()
        val callStack: Stack<Activation> = LifoStack()
        val heap: Heap = ScopedHeap()

        var pc = 0
        try {
            val diag = false
            val cmdMs = HashMap<String, Long>()
            val cmdCnt = HashMap<String, Long>()
            while (pc < program.size) {
                val cmd = program[pc]
//                println("[$pc] $cmd")
                var t: Long = 0
                if (diag) {
                    t = System.currentTimeMillis()
                }
                pc = cmd.exec(pc, dataStack, callStack, heap)
                if (diag) {
                    val e = System.currentTimeMillis() - t
                    val name = cmd.javaClass.name
                    val pe = cmdMs[name] ?: 0
                    cmdMs[name] = pe + e
                    val pi = cmdCnt[name] ?: 0
                    cmdCnt[name] = pi + 1
                }
            }
            if (diag) {
                for (entry in cmdMs) {
                    val times = cmdCnt[entry.key] ?: 0
                    val mil: Double = entry.value.toDouble() * 100000000 / times.toDouble()
                    println(entry.key + "(" + times + ") -> " + entry.value + ": " + mil.toInt())
                }
            }
            println("program ended")
        } catch (ignored: HaltException) {
            println("program halted")
        } catch (ex: Throwable) {
            println("exception was thrown on $pc: " + program[pc])
            ex.printStackTrace()
        }
        val elapsed = System.currentTimeMillis() - time
        println("vm stopped in $elapsed ms")
    }

}