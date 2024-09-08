package vm2.operations

import vm2.*
import vm2.records.ValueRecord

class Slice : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val size = dataStack.pop().getInt()
        val slice = Array(size, { i ->
            dataStack.pop()
        }).apply { reverse() }
        val rec = ValueRecord(slice)
        dataStack.push(rec)
    }

}