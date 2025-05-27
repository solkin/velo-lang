package vm.operations

import vm.Frame
import vm.Operation
import vm.FrameLoader
import vm.Stack

class Goto(
    val addr: Int
) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, frameLoader: FrameLoader): Int {
        return addr
    }

}