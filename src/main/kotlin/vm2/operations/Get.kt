package vm2.operations

import vm2.Activation
import vm2.Heap
import vm2.Record
import vm2.SimpleOperation
import vm2.Stack

class Get(
    val index: Int,
): SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val scope = heap.current()
        val rec = scope.get(index)
        dataStack.push(rec)
    }

}