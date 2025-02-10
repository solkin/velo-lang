package vm.operations

import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack

class Get(
    val index: Int,
): SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val scope = heap.current()
        val rec = scope.get(index)
        subs.push(rec)
    }

}