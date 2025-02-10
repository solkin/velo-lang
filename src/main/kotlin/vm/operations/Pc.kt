package vm.operations

import vm.Frame
import vm.Heap
import vm.Operation
import vm.Stack
import vm.records.ValueRecord

class Pc : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, heap: Heap): Int {
        val rec = ValueRecord(pc)
        stack.peek().subs.push(rec)
        return pc + 1
    }

}