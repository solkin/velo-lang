package vm.operations

import vm.Activation
import vm.Heap
import vm.Operation
import vm.Record
import vm.Stack
import vm.records.ValueRecord

class Pc : Operation {

    override fun exec(pc: Int, dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap): Int {
        val rec = ValueRecord(pc)
        dataStack.push(rec)
        return pc + 1
    }

}