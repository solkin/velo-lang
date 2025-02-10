package vm.operations

import vm.Frame
import vm.Heap
import vm.Operation
import vm.Stack

class Goto(
    private val addr: Int
) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, heap: Heap): Int {
        return addr
    }

}