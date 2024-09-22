package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class ArrOf : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val size = dataStack.pop().getInt()
        val array = Array(size, { i ->
            dataStack.pop()
        }).apply { reverse() }
        val rec = ValueRecord(array)
        dataStack.push(rec)
    }

}