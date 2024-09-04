package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import vm2.Stack

class Not: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val rec = dataStack.pop()

        val result = ValueRecord(rec.getBool().not())

        dataStack.push(result)
    }

}