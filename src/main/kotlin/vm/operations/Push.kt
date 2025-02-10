package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class Push(
    val value: Any
): SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val rec = ValueRecord(value)
        subs.push(rec)
    }

}