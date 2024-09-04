package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import vm2.Stack

class Def(
    val index: Int,
): SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val value = dataStack.pop().get()
        val scope = heap.current()
        scope.def(index, ValueRecord(value))
    }

}