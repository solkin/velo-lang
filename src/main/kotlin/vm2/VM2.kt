package vm2

import java.util.*
import kotlin.collections.ArrayList

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

        val dataStack: Deque<Record> = LinkedList()
        val callStack: Deque<Activation> = LinkedList()
        val heap: Heap = ScopedHeap()

        var pc = 0
        try {
            while (pc < program.size) {
                val cmd = program[pc]
//                println("[$pc] $cmd")
                pc = cmd.exec(pc, dataStack, callStack, heap)
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