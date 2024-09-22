package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class ArrPlus : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val v = dataStack.pop()
        val arr = dataStack.pop().getArray()

        val rec = ValueRecord(arr.plus(v))

        dataStack.push(rec)
    }

}
