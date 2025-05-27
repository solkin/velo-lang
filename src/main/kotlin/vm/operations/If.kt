package vm.operations

import vm.Frame
import vm.Operation
import vm.FrameLoader
import vm.Stack

class If(
    val elseSkip: Int
) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, frameLoader: FrameLoader): Int {
        val flag = stack.peek().subs.pop().getBool()
        return pc + 1 + if (flag) 0 else elseSkip
    }

}