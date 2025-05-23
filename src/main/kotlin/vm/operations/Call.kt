package vm.operations

import vm.Frame
import vm.LifoStack
import vm.Operation
import vm.Stack
import vm.createVars

class Call(val args: Int) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>): Int {
        val thisFrame = stack.peek()
        val addr = thisFrame.subs.pop().getInt()
        val newFrame = Frame(pc = pc + 1, subs = LifoStack(), vars = createVars(thisFrame.vars))
        Array(size = args, init = {
            thisFrame.subs.pop()
        }).reversedArray().forEach { arg ->
            newFrame.subs.push(arg)
        }
        stack.push(newFrame)
        return addr
    }

}