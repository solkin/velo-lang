package vm.operations

import vm.Frame
import vm.Operation
import vm.Stack

class Goto(
    val addr: Int
) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>): Int {
        return addr
    }

}