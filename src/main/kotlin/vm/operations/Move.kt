package vm.operations

import vm.Frame
import vm.Operation
import vm.Stack

class Move(
    private val count: Int
) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>): Int {
        return pc + count + 1
    }

}