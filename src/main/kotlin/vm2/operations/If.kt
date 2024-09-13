package vm2.operations

import vm2.Activation
import vm2.Heap
import vm2.Operation
import vm2.Record
import vm2.Stack

class If(
    private val elseSkip: Int
) : Operation {

    override fun exec(pc: Int, dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap): Int {
        val flag = dataStack.pop().getBool()
        return pc + 1 + if (flag) 0 else elseSkip
    }

}