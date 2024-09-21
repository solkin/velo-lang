package vm.operations

import vm.Activation
import vm.Heap
import vm.Operation
import vm.Record
import vm.Stack

class Move(
    private val count: Int
) : Operation {

    override fun exec(pc: Int, dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap): Int {
        return pc + count + 1
    }

}