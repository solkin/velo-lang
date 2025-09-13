package vm.operations

import vm.Frame
import vm.Operation
import vm.FrameLoader
import vm.Stack
import vm.records.ClassRecord

class Instance(val nativeIndex: Int?) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, frameLoader: FrameLoader): Int {
        val frame = stack.peek()
        val record = ClassRecord.create(frame, nativeIndex)
        frame.subs.push(value = record)
        return pc + 1
    }

}