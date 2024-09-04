package vm2.operations

import vm2.*
import vm2.records.ValueRecord

class Push(
    val value: Any
): SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val rec = ValueRecord(value)
        dataStack.push(rec)
    }

}