package vm.operations

import vm.Frame
import vm.Heap
import vm.LifoStack
import vm.Operation
import vm.Stack

class Call(val args: Int) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, heap: Heap): Int {
        val addr = stack.peek().subs.pop().getInt()
        val frame = Frame(addr = pc + 1, subs = LifoStack())
        Array(size = args, init = {
            stack.peek().subs.pop()
        }).reversedArray().forEach { arg ->
            frame.subs.push(arg)
        }
        stack.push(frame)
        return addr
    }

}