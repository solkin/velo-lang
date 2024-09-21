package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class Def(
    val index: Int,
): SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val value = dataStack.pop().get()
        val scope = heap.current()
        scope.def(index, ValueRecord(value))
    }

}