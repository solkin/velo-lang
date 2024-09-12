package vm2.operations

import vm2.*
import vm2.Stack
import vm2.records.ValueRecord

class StrLen : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val str = dataStack.pop().getString()

        val rec = ValueRecord(str.length)

        dataStack.push(rec)
    }

}