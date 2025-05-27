package vm.operations

import vm.Frame
import vm.Operation
import vm.FrameLoader
import vm.Stack

class Move(
    val count: Int
) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, frameLoader: FrameLoader): Int {
        return pc + count + 1
    }

}