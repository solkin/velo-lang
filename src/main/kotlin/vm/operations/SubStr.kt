package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class SubStr : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val start = dataStack.pop().getInt()
        val end = dataStack.pop().getInt()
        val str = dataStack.pop().getString()

        val rec = ValueRecord(str.substring(start, end))

        dataStack.push(rec)
    }

}