package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class StrLen : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val str = dataStack.pop().getString()

        val rec = ValueRecord(str.length)

        dataStack.push(rec)
    }

}