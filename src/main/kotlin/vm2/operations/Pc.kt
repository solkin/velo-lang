package vm2.operations

import vm2.Activation
import vm2.Heap
import vm2.Operation
import vm2.Record
import vm2.Stack
import vm2.records.ValueRecord

class Pc : Operation {

    override fun exec(pc: Int, dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap): Int {
        val rec = ValueRecord(pc)
        dataStack.push(rec)
        return pc + 1
    }

}