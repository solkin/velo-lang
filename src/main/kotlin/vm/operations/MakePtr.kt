package vm.operations

import vm.Frame
import vm.Operation
import vm.Stack
import vm.records.PointerRecord

class MakePtr(
    val diff: Int
) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>): Int {
        val rec = PointerRecord(pointer = pc + diff)
        stack.peek().subs.push(rec)
        return pc + 1
    }

}