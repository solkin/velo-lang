package vm.operations

import vm.Frame
import vm.Operation
import vm.FrameLoader
import vm.Stack

class Noop() : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, frameLoader: FrameLoader): Int {
        return pc + 1
    }

}