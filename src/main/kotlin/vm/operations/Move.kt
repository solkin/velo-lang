package vm.operations

import vm.Frame
import vm.Heap
import vm.Operation
import vm.Stack

class Move(
    private val count: Int
) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, heap: Heap): Int {
        return pc + count + 1
    }

}