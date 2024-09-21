package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack

class Get(
    val index: Int,
): SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val scope = heap.current()
        val rec = scope.get(index)
        dataStack.push(rec)
    }

}