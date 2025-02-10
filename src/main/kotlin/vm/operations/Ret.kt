package vm.operations

import vm.Frame
import vm.Heap
import vm.Operation
import vm.Stack

class Ret : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, heap: Heap): Int {
        val frame = stack.pop()
        if (!frame.subs.empty()) {
            val value = frame.subs.pop()
            stack.peek().subs.push(value)
        }
        return frame.addr
    }

}