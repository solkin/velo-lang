package vm.operations

import vm.Frame
import vm.LifoStack
import vm.Operation
import vm.Resources
import vm.Stack
import vm.createVars

class Call(val args: Int) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, resources: Resources): Int {
        val thisFrame = stack.peek()
        val frameNum = thisFrame.subs.pop().getInt()
        val frame = resources.frames[frameNum] ?: throw Exception("Frame $frameNum not found")
        val newFrame = Frame(pc = 0, subs = LifoStack(), vars = createVars(thisFrame.vars), ops = frame.ops)
        Array(size = args, init = {
            thisFrame.subs.pop()
        }).reversedArray().forEach { arg ->
            newFrame.subs.push(arg)
        }
        stack.push(newFrame)
        return pc + 1
    }

}