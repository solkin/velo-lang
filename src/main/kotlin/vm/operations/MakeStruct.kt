package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class MakeStruct : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val count = dataStack.pop().getInt()

        val elements = ArrayList<Record>()
        for (i in 0 until count) {
            val rec = dataStack.pop()
            elements.add(rec)
        }
        val result = ValueRecord(elements)

        dataStack.push(result)
    }

}
