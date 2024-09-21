package vm.operations

import vm.Activation
import vm.Heap
import vm.Operation
import vm.Record
import vm.Stack

class Goto(
    private val addr: Int
) : Operation {

    override fun exec(pc: Int, dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap): Int {
        return addr
    }

}