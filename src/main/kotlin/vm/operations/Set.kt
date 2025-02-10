package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class Set(
    val index: Int,
): SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val value = subs.pop().get()
        val scope = heap.current()
        scope.set(index, ValueRecord(value))
    }

}