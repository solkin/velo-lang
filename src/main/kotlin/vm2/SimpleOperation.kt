package vm2

import java.util.*

interface SimpleOperation : Operation {

    override fun exec(pc: Int, dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap): Int {
        exec(dataStack, callStack, heap)
        return pc + 1
    }

    fun exec(dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap)

}
