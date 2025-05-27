package vm.operations

import vm.Frame
import vm.Operation
import vm.FrameLoader
import vm.Stack
import vm.records.FrameRecord

class Frame(
    val num: Int
) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, frameLoader: FrameLoader): Int {
        val rec = FrameRecord(num)
        stack.peek().subs.push(rec)
        return pc + 1
    }

}