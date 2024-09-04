package vm2.operations

import vm2.*
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