package vm2.operations

import vm2.*
import vm2.Stack
import vm2.records.ValueRecord

class SubStr : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val start = dataStack.pop().getInt()
        val end = dataStack.pop().getInt()
        val str = dataStack.pop().getString()

        val rec = ValueRecord(str.substring(start, end))

        dataStack.push(rec)
    }

}