package vm.operations

import vm.Activation
import vm.Heap
import vm.Operation
import vm.Record
import vm.Stack

class If(
    private val elseSkip: Int
) : Operation {

    override fun exec(pc: Int, dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap): Int {
        val flag = dataStack.pop().getBool()
        return pc + 1 + if (flag) 0 else elseSkip
    }

}