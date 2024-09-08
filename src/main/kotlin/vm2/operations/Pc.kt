package vm2.operations

import vm2.*
import vm2.records.ValueRecord

class Pc : Operation {

    override fun exec(pc: Int, dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap): Int {
        val rec = ValueRecord(pc)
        dataStack.push(rec)
        return pc + 1
    }

}