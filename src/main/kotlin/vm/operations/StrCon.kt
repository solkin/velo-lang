package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class StrCon : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val str1 = dataStack.pop().getString()
        val str2 = dataStack.pop().getString()

        val rec = ValueRecord(str2 + str1)

        dataStack.push(rec)
    }

}