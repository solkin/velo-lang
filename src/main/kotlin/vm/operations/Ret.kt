package vm.operations

import vm.Frame
import vm.Operation
import vm.FrameLoader
import vm.Stack

class Ret : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, frameLoader: FrameLoader): Int {
        val frame = stack.pop()
        if (!frame.subs.empty()) {
            val value = frame.subs.pop()
            stack.peek().subs.push(value)
        }
        return pc // This is program counter value for the dropped frame, the VM will not use it
    }

}