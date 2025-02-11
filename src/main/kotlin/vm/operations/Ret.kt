package vm.operations

import vm.Frame
import vm.Operation
import vm.Stack

class Ret : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>): Int {
        val frame = stack.pop()
        if (!frame.subs.empty()) {
            val value = frame.subs.pop()
            stack.peek().subs.push(value)
        }
        return frame.addr
    }

}