package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class IntStr : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val v = dataStack.pop().getInt()

        val rec = ValueRecord(v.toString())

        dataStack.push(rec)
    }

}