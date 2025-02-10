package vm.operations

import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class Def(
    val index: Int,
): SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val value = subs.pop().get()
        val scope = heap.current()
        scope.def(index, ValueRecord(value))
    }

}