package vm.operations

import vm.Operation
import vm.VMContext
import kotlin.math.abs

class Call(val args: Int, val classParent: Boolean = false) : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val thisFrame = ctx.currentFrame()
        val frameNum = thisFrame.subs.pop().getInt()
        val argsArray = Array(size = abs(args), init = { thisFrame.subs.pop() })
            .let { arr -> if (args > 0) arr.reversedArray() else arr }
        val parent = if (classParent) thisFrame.subs.pop().getFrame() else thisFrame
        val newFrame = ctx.loadFrame(num = frameNum, parent)
            ?: throw Exception("Frame $frameNum not found")
        argsArray.forEach { arg -> newFrame.subs.push(arg) }
        ctx.pushFrame(newFrame)
        return pc + 1
    }

}