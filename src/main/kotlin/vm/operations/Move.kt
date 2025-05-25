package vm.operations

import vm.Frame
import vm.Operation
import vm.Resources
import vm.Stack

class Move(
    val count: Int
) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, resources: Resources): Int {
        return pc + count + 1
    }

}