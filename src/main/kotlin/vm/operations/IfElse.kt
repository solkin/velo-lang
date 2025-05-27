package vm.operations

import vm.Frame
import vm.Operation
import vm.FrameLoader
import vm.Stack
import vm.records.FrameRecord

class IfElse(
    val thenNum: Int,
    val elseNum: Int,
) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, frameLoader: FrameLoader): Int {
        val flag = stack.peek().subs.pop().getBool()
        val num = if (flag) thenNum else elseNum
        stack.peek().subs.push(value = FrameRecord(num))
        return pc + 1
    }

}