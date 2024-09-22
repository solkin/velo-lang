package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class ArrCon : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val arr1 = dataStack.pop().getArray()
        val arr2 = dataStack.pop().getArray()

        val rec = ValueRecord(arr2.plus(arr1))

        dataStack.push(rec)
    }

}
