package vm.operations

import vm.Frame
import vm.FrameLoader
import vm.Operation
import vm.Stack

class Call(val args: Int) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, frameLoader: FrameLoader): Int {
        val thisFrame = stack.peek()
        val frameNum = thisFrame.subs.pop().getInt()
        val newFrame = frameLoader.loadFrame(num = frameNum, parent = thisFrame)
            ?: throw Exception("Frame $frameNum not found")
        Array(size = args, init = {
            thisFrame.subs.pop()
        }).reversedArray().forEach { arg ->
            newFrame.subs.push(arg)
        }
        stack.push(newFrame)
        return pc + 1
    }

}