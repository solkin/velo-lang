package vm.operations

import vm.Frame
import vm.FrameLoader
import vm.Operation
import vm.Stack
import kotlin.math.abs

class Call(val args: Int, val classParent: Boolean = false) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, frameLoader: FrameLoader): Int {
        val thisFrame = stack.peek()
        val frameNum = thisFrame.subs.pop().getInt()
        val argsArray = Array(size = abs(args), init = { thisFrame.subs.pop() })
            .let { arr -> if (args > 0) arr.reversedArray() else arr }
        val parent = if (classParent) thisFrame.subs.pop().getFrame() else thisFrame
        val newFrame = frameLoader.loadFrame(num = frameNum, parent)
            ?: throw Exception("Frame $frameNum not found")
        argsArray.forEach { arg -> newFrame.subs.push(arg) }
        stack.push(newFrame)
        return pc + 1
    }

}