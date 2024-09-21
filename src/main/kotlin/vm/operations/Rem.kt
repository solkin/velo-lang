package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class Rem: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val rec1 = dataStack.pop().getInt()
        val rec2 = dataStack.pop().getInt()

        val result = ValueRecord(rec2 % rec1)

        dataStack.push(result)
    }

}